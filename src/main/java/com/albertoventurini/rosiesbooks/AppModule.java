package com.albertoventurini.rosiesbooks;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Declares an application feature and its permitted cross-feature API dependencies. */
@Documented
@Retention(RUNTIME)
@Target(PACKAGE)
public @interface AppModule {

  String name();

  String[] allowedDependencies();
}
