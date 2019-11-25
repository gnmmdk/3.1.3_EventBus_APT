package com.kangjj.eventbus.compiler.utils;

import com.google.auto.service.AutoService;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
@AutoService(Processor.class)
@SupportedAnnotationTypes({Constants.SUBSCRIBE_ANNOTATION_TYPES})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({Constants.PACKAGE_NAME,Constants.CLASS_NAME})
public class SubscribeProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
