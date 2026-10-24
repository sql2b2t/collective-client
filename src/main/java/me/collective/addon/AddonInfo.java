package me.collective.addon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AddonInfo {
    String name();
    String version() default "1.0.0";
    String author() default "Collective Developer";
    String description() default "";
    String website() default "";
}