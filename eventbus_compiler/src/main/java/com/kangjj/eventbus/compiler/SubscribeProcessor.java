package com.kangjj.eventbus.compiler;

import com.google.auto.service.AutoService;
import com.kangjj.eventbus.annotation.Subscribe;
import com.kangjj.eventbus.annotation.mode.EventBeans;
import com.kangjj.eventbus.annotation.mode.SubscriberInfo;
import com.kangjj.eventbus.annotation.mode.SubscriberMethod;
import com.kangjj.eventbus.annotation.mode.ThreadMode;
import com.kangjj.eventbus.compiler.utils.Constants;
import com.kangjj.eventbus.compiler.utils.EmptyUtils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.awt.Dialog;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)//TODO  模块化
@SupportedAnnotationTypes({Constants.SUBSCRIBE_ANNOTATION_TYPES})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({Constants.PACKAGE_NAME,Constants.CLASS_NAME})
public class SubscribeProcessor extends AbstractProcessor {
    //操作Element工具类（类、函数、属性都是Element）
    private Elements elementUtils;
    //type（类信息）工具类，包含用于操作TypeMirror的工具方法
    private Types typeUtils;
    //Messager用来报告错误，警告和其他提示信息
    private Messager messager;
    //文件生成器 类/资源，Filer用来创建新的类文件，class文件以及辅助文件
    private Filer filer;
    //APT包名
    private String packageName;
    //APT类名
    private String className;
    //临时map存储，用来存放订阅方法信息，生成路由组类文件时遍历
    //key：组名"MainActivity",value:MainActivity中订阅方法集合
    private final Map<TypeElement, List<ExecutableElement>> methodsByClass = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();

        Map<String,String> options = processingEnv.getOptions();
        if(!EmptyUtils.isEmpty(options)){
            packageName = options.get(Constants.PACKAGE_NAME);
            className = options.get(Constants.CLASS_NAME);
            messager.printMessage(Diagnostic.Kind.NOTE,"packageName >>> " + packageName + " / className >>> " + className);
        }
        if(EmptyUtils.isEmpty(packageName) || EmptyUtils.isEmpty(className)){
            messager.printMessage(Diagnostic.Kind.ERROR,"注解处理器需要的参数为空，请在对应build.gradle配置参数");
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        if(!EmptyUtils.isEmpty(set)){//一旦有类之上使用@Subscribe注解
            //获取所有被@Subscribe 注解的元素集合
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Subscribe.class);
            if(!EmptyUtils.isEmpty(elements)){
                try {
                    parseElements(elements);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }

            return true;
        }
        return false;
    }

    /**
     * 解析所有被@Subscribe注解的类元素集合
     * @param elements
     * @throws Exception
     */
    private void parseElements(Set<? extends Element> elements) throws Exception {
        for (Element element : elements) {
            // @Subscribe注解只能在方法之上（尽量避免使用instanceof进行判断）
            if(element.getKind() != ElementKind.METHOD){
                messager.printMessage(Diagnostic.Kind.ERROR,"仅解析@Subscribe注解在方法上元素");
                return;
            }
            ExecutableElement method = (ExecutableElement) element;
            //检查方法，条件：订阅方法必须是非静态的，公开的，参数只能有一个
            if(checkHasNoErrors(method)){
                //获取类中的所以被Subscribe注解的方法，并且保存在缓存中

                //获取封装订阅方法的类（方法上一个节点）
                TypeElement classElement = (TypeElement) method.getEnclosingElement();
                List<ExecutableElement> methods = methodsByClass.get(classElement);
                if(methods == null){
                    methods = new ArrayList<>();
                    methodsByClass.put(classElement,methods);
                }
                methods.add(method);
            }
            messager.printMessage(Diagnostic.Kind.NOTE, "遍历注解方法：" + method.getSimpleName().toString());
        }
        //通过Element工具类，后去SubscriberInfoIndex类型
        TypeElement subscriberIndexType = elementUtils.getTypeElement(Constants.SUBSCRIBERINFO_INDEX);
        //生成类文件
        createFile(subscriberIndexType);
    }

    private void createFile(TypeElement subscriberIndexType) throws IOException {
        // 全局属性：private static final Map<Class<?>, SubscriberMethod> SUBSCRIBER_INDEX 中的
        // Map<Class<?>, SubscriberMethod>,其余字段(private static final  SUBSCRIBER_INDEX  )在TypeSpec进行添加
        TypeName fieldType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(Class.class),
                ClassName.get(SubscriberInfo.class)
        );
        //添加静态代码块 SUBSCRIBER_INDEX = new HashMap<Class, SubscriberInfo>();
        CodeBlock.Builder codeBlock = CodeBlock.builder();
        codeBlock.addStatement("$N = new $T<$T,$T>()",
                Constants.FIELD_NAME,
                HashMap.class,
                Class.class,
                SubscriberInfo.class);
        //双层循环，第一层遍历被@Subscribe注解的方法所属的类。第二层遍历每个类中的所有订阅的方法
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByClass.entrySet()) {
            //此处不能使用codeBlock,会造成错误嵌套
            CodeBlock.Builder contentBlock = CodeBlock.builder();
            CodeBlock contentCode = null;
            String format ;
            for (int i = 0; i < entry.getValue().size(); i++) {
                ExecutableElement executableElement = entry.getValue().get(i);
                // 获取每个方法上的@Subscribe注解中的注解值
                Subscribe subscribe = executableElement.getAnnotation(Subscribe.class);
                //获取订阅事件方法的所有参数
                List<? extends VariableElement> parameters = executableElement.getParameters();
                //获取订阅的事件的方法名
                String methodName = executableElement.getSimpleName().toString();
                //注意：此处还可以做检查工作，比如：参数类型必须是类或接口类型（这里缩减了）
                TypeElement parameterElement = (TypeElement) typeUtils.asElement(parameters.get(0).asType());
                if(i == entry.getValue().size()-1){
                    format= "new $T($T.class, $S, $T.class, $T.$L, $L, $L)";
                }else{
                    format = "new $T($T.class, $S, $T.class, $T.$L, $L, $L),\n";
                }
                // new SubscriberMethod(MainActivity.class, "abc", UserInfo.class, ThreadMode.POSTING, 0, false)
                contentCode = contentBlock.add(format,
                        SubscriberMethod.class,
                        ClassName.get(entry.getKey()),
                        methodName,
                        ClassName.get(parameterElement),
                        ThreadMode.class,
                        subscribe.threadMode(),
                        subscribe.priority(),
                        subscribe.sticky())
                        .build();
            }//end for inner

            if(contentCode != null){
                // putIndex(new EventBeans(MainActivity.class, new SubscriberMethod[] {)
                String putIndexFormat = "putIndex(new $T($T.class,new $T[]";
                codeBlock.beginControlFlow(putIndexFormat,
                        EventBeans.class,
                        ClassName.get(entry.getKey()),
                        SubscriberMethod.class)
                    .add(contentCode)                   // 嵌套的精华（尝试了很多次，有更好的方式请告诉我）
                    .endControlFlow("))");
            }else{
                messager.printMessage(Diagnostic.Kind.ERROR,"注解处理器双层循环发生错误！");
            }
        }//end for out
        /************************************* method 1***************************************/
        ParameterSpec putIndexParameter = ParameterSpec.builder(
                SubscriberInfo.class,
                Constants.PUTINDEX_PARAMETER_NAME)
                .build();
        //putIndex方法配置：  private static void putIndex(SubscriberInfo info) {
        MethodSpec.Builder putIndexBuilder = MethodSpec
                .methodBuilder(Constants.PUTINDEX_METHOD_NAME)              // 方法名
                .addModifiers(Modifier.PRIVATE,Modifier.STATIC)             // private static修饰符
                .addParameter(putIndexParameter);                           // 添加方法参数
        // 不填returns默认void返回值
        // putIndex方法内容：SUBSCRIBER_INDEX.put(info.getSubscriberClass(), info);
        putIndexBuilder.addStatement("$N.put($N.getSubscriberClass(),$N)",
                Constants.FIELD_NAME,
                Constants.PUTINDEX_PARAMETER_NAME,
                Constants.PUTINDEX_PARAMETER_NAME);
        /************************************* method 1***************************************/
        /************************************* method 2***************************************/
        ParameterSpec getSubscriberInfoParameter = ParameterSpec.builder(
                ClassName.get(Class.class)
                ,Constants.GETSUBSCRIBERINFO_PARAMETER_NAME
                ).build();
        // getSubscriberInfo方法配置：public SubscriberMethod getSubscriberInfo(Class<?> subscriberClass) {
        MethodSpec.Builder getSubscriberInfoBuilder = MethodSpec
                .methodBuilder(Constants.GETSUBSCRIBERINFO_METHOD_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(getSubscriberInfoParameter)
                .returns(SubscriberInfo.class);
        //return SUBSCRIBER_INDEX.get(subscriberClass);
        getSubscriberInfoBuilder.addStatement("return $N.get($N)",
                Constants.FIELD_NAME,
                Constants.GETSUBSCRIBERINFO_PARAMETER_NAME);
        /************************************* method 2***************************************/
        //public final class EventBusIndex implements SubscriberInfoIndex {
        TypeSpec typeSpec = TypeSpec.classBuilder(className)
                .addSuperinterface(ClassName.get(subscriberIndexType))                                      //实现SubscriberInfoIndex接口
                .addModifiers(Modifier.PUBLIC,Modifier.FINAL)                                               //修饰符
                .addStaticBlock(codeBlock.build())                                                          //添加静态块
                .addField(fieldType,Constants.FIELD_NAME,Modifier.PRIVATE,Modifier.STATIC,Modifier.FINAL)   // 全局属性：private static final Map<Class<?>, SubscriberMethod> SUBSCRIBER_INDEX
                .addMethod(putIndexBuilder.build())                                                         // 第一个方法：加入全局Map集合
                .addMethod(getSubscriberInfoBuilder.build())                                                // 第二个方法：通过订阅者对象（MainActivity.class）获取所有订阅方法
                .build();
        JavaFile.builder(packageName,       //包名
                typeSpec)                   //类构建完成
                .build()                    //JavaFile构建完成
                .writeTo(filer);            //文件生成器开始生成类文件
    }

    /**
     * 检查发放，条件：订阅方法必须是非静态的，公开的，参数只能有一个
     * @param element 方法元素组
     * @return  检查是否通过
     */
    private boolean checkHasNoErrors(ExecutableElement element) {
        //不能为static静态方法
        if(element.getModifiers().contains(Modifier.STATIC)){
            messager.printMessage(Diagnostic.Kind.ERROR,"订阅事件方法不能是static静态方法",element);
            return false;
        }
        //必须是public修饰的方法
        if(!element.getModifiers().contains(Modifier.PUBLIC)){
            messager.printMessage(Diagnostic.Kind.ERROR,"订阅事件方法必须是public修饰的方法",element);
            return false;
        }
        // 订阅事件方法必须只有一个参数
        List<? extends VariableElement> parameters = element.getParameters();
        if(EmptyUtils.isEmpty(parameters) || parameters.size() != 1){
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法有且仅有一个参数", element);
            return false;
        }
        return true;
    }
}
