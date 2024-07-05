package net.neoforged.jst.api;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiManager;

public interface IntelliJEnvironment {
    CoreApplicationEnvironment getAppEnv();

    JavaCoreProjectEnvironment getProjectEnv();

    PsiManager getPsiManager();

    JavaPsiFacade getPsiFacade();
}
