/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.ty

import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocClassDef
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.LuaPredefinedScope
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassFieldIndex
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaClassMethodIndex

interface ITyClass : ITy {
    val className: String
    var superClassName: String?
    var aliasName: String?
    fun lazyInit(searchContext: SearchContext)
    fun processFields(context: SearchContext, processor: (ITyClass, LuaClassField) -> Unit)
    fun processMethods(context: SearchContext, processor: (ITyClass, LuaClassMethod) -> Unit)
    fun processStaticMethods(context: SearchContext, processor: (ITyClass, LuaClassMethod) -> Unit)
    fun findMethod(name: String, searchContext: SearchContext): LuaClassMethod?
    fun findField(name: String, searchContext: SearchContext): LuaClassField?
    fun getSuperClass(context: SearchContext): ITyClass?
}

abstract class TyClass(override val className: String, override var superClassName: String? = null) : Ty(TyKind.Class), ITyClass {

    final override var aliasName: String? = null

    private var _lazyInitialized: Boolean = false

    override fun processFields(context: SearchContext, processor: (ITyClass, LuaClassField) -> Unit) {
        val clazzName = className
        val project = context.project

        val fieldIndex = LuaClassFieldIndex.getInstance()
        val list = fieldIndex.get(clazzName, project, LuaPredefinedScope(project))

        val alias = aliasName
        if (alias != null) {
            val classFields = fieldIndex.get(alias, project, LuaPredefinedScope(project))
            list.addAll(classFields)
        }

        for (field in list) {
            processor(this, field)
        }

        // super
        val superType = getSuperClass(context)
        superType?.processFields(context, processor)
    }

    override fun processMethods(context: SearchContext, processor: (ITyClass, LuaClassMethod) -> Unit) {
        val clazzName = className
        val project = context.project

        val methodIndex = LuaClassMethodIndex.getInstance()
        val list = methodIndex.get(clazzName, project, LuaPredefinedScope(project))

        val alias = aliasName
        if (alias != null) {
            list.addAll(methodIndex.get(alias, project, LuaPredefinedScope(project)))
        }
        for (def in list) {
            val methodName = def.name
            if (methodName != null) {
                processor(this, def)
            }
        }

        val superType = getSuperClass(context)
        superType?.processMethods(context, processor)
    }

    override fun processStaticMethods(context: SearchContext, processor: (ITyClass, LuaClassMethod) -> Unit) {
        val clazzName = className
        val list = LuaClassMethodIndex.findStaticMethods(clazzName, context)

        val alias = aliasName
        if (alias != null) {
            list.addAll(LuaClassMethodIndex.findStaticMethods(alias, context))
        }
        for (def in list) {
            val methodName = def.name
            if (methodName != null) {
                processor(this, def)
            }
        }

        val superType = getSuperClass(context)
        superType?.processStaticMethods(context, processor)
    }

    override val displayName: String get() = className

    override fun lazyInit(searchContext: SearchContext) {
        if (!_lazyInitialized) {
            _lazyInitialized = true
            doLazyInit(searchContext)
        }
    }

    open fun doLazyInit(searchContext: SearchContext) {
        val classDef = LuaClassIndex.find(className, searchContext)
        if (classDef != null) {
            val tyClass = classDef.classType
            tyClass.lazyInit(searchContext)
            aliasName = tyClass.aliasName
            superClassName = tyClass.superClassName
        }
    }

    override fun getSuperClass(context: SearchContext): ITyClass? {
        val clsName = superClassName
        if (clsName != null) {
            val def = LuaClassIndex.find(clsName, context)
            return def?.classType
        }
        return null
    }

    override fun findMethod(name: String, searchContext: SearchContext): LuaClassMethod? {
        val className = className
        var def = LuaClassMethodIndex.findMethodWithName(className, name, searchContext)
        if (def == null) { // static
            def = LuaClassMethodIndex.findStaticMethod(className, name, searchContext)
        }
        if (def == null) { // super
            val superType = getSuperClass(searchContext)
            if (superType != null)
                def = superType.findMethod(name, searchContext)
        }
        return def
    }

    override fun findField(name: String, searchContext: SearchContext): LuaClassField? {
        var def = LuaClassFieldIndex.find(this, name, searchContext)
        if (def == null) {
            val superType = getSuperClass(searchContext)
            if (superType != null)
                def = superType.findField(name, searchContext)
        }
        return def
    }

    companion object {
        // for _G
        val G:TyClass = TySerializedClass(Constants.WORD_G)

        fun createAnonymousType(nameDef: LuaNameDef): TyClass {
            return TySerializedClass(nameDef.name)
        }

        fun createGlobalType(nameExpr: LuaNameExpr): TyClass {
            return TySerializedClass(nameExpr.text)
        }
    }
}

class TyPsiDocClass(val classDef: LuaDocClassDef) : TyClass(classDef.name) {
    private val _supperName: String? by lazy {
        val supperRef = classDef.superClassNameRef
        if (supperRef != null)
            return@lazy supperRef.text
        else null
    }

    override fun doLazyInit(searchContext: SearchContext) {
        aliasName = classDef.aliasName
        superClassName = _supperName
    }
}

open class TySerializedClass(name: String, supper: String? = null, alias: String? = null)
    : TyClass(name, supper) {
    init {
        aliasName = alias
    }
}

//todo Lazy class ty
class TyLazyClass(name: String) : TySerializedClass(name)

fun getTableTypeName(table: LuaTableExpr): String {
    val fileName = table.containingFile.name
    return "$fileName@(${table.node.startOffset})table"
}

class TyTable(val table: LuaTableExpr) : TyClass(getTableTypeName(table)) {
    override fun processFields(context: SearchContext, processor: (ITyClass, LuaClassField) -> Unit) {
        for (field in table.tableFieldList) {
            processor(this, field)
        }
        super.processFields(context, processor)
    }

    override val displayName: String
        get() = "table"

    override fun doLazyInit(searchContext: SearchContext) = Unit
}