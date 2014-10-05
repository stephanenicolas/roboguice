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

    def extension = getExtension()
    def pluginExtension = getPluginExtension()
    if (extension && pluginExtension) {
      project.extensions.create(extension, pluginExtension)
    }

    final def log = project.logger
    final String LOG_TAG = this.getClass().getName()

    configure(project)
    log.debug(LOG_TAG, "Project variant configured for RoboGuice.")

    if(!hasApp || !hasLib) {
      log.debug(LOG_TAG, "Project is not android lib or app project. Roboguice plugin can't be applied.")
    }
    final def variants
    if (hasApp) {
      variants = project.android.applicationVariants
    } else {
      variants = project.android.libraryVariants
    }




    variants.all { variant ->
      if (skipVariant(variant)) {
        return;
      }

      variant.javaCompile.options.compilerArgs += [
          '-s', project.file('build/generated')
      ]

      JavaCompile javaCompile = variant.javaCompile
      FileCollection classpathFileCollection = project.files(project.android.bootClasspath)
      classpathFileCollection += javaCompile.classpath

      println "Classpath " + classpathFileCollection
    }
  }

  protected void configure(Project project) {
    project.plugins.apply('com.github.stephanenicolas.injectview')
    project.plugins.apply('com.github.stephanenicolas.injectresource')
    project.plugins.apply('com.github.stephanenicolas.injectextra')

    project.dependencies {
      provided 'org.roboguice:roboblender:4.0.0-SNAPSHOT'
      compile 'org.roboguice:roboguice:4.0.0-SNAPSHOT'
    }
  }

  protected Class getPluginExtension() {
    RoboGuicePluginExtension
  }

  protected String getExtension() {
    "roboguice"
  }
}
