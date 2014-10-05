package org.roboguice

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.JavaCompile

/**
 * @author SNI
 */
public class RoboGuicePlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    def hasApp = project.plugins.withType(AppPlugin)
    def hasLib = project.plugins.withType(LibraryPlugin)
    if (!hasApp && !hasLib) {
      throw new IllegalStateException("'android' or 'android-library' plugin required.")
    }

    def extension = getExtension()
    def pluginExtension = getPluginExtension()
    if (extension && pluginExtension) {
      project.extensions.create(extension, pluginExtension)
    }

    final def log = project.logger
    final String LOG_TAG = this.getClass().getName()

    final def variants
    if (hasApp) {
      variants = project.android.applicationVariants
    } else {
      variants = project.android.libraryVariants
    }

    configure(project)

    variants.all { variant ->
      if (skipVariant(variant)) {
        return;
      }
      log.debug(LOG_TAG, "Transforming classes in variant '${variant.name}'.")

      JavaCompile javaCompile = variant.javaCompile
      FileCollection classpathFileCollection = project.files(project.android.bootClasspath)
      classpathFileCollection += javaCompile.classpath

      println "Classpath " + javaCompile.classpath.asPath
    }
  }

  protected void configure(Project project) {
    project.plugins.
        project.dependencies {
      provided 'com.github.stephanenicolas.injectview:injectview-annotations:1.+'
    }
  }

  protected Class getPluginExtension() {
    RoboGuicePluginExtension
  }

  protected String getExtension() {
    "roboguice"
  }
}
