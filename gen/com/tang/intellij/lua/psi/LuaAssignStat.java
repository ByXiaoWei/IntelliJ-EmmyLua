// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface LuaAssignStat extends LuaStatement, LuaDeclaration {

  @NotNull
  List<LuaExprList> getExprListList();

  @NotNull
  PsiElement getAssign();

  @NotNull
  LuaExprList getVarExprList();

  @Nullable
  LuaExprList getValueExprList();

}
