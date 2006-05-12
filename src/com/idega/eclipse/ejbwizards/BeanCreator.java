/*
 * $Id$ Created on Apr 19, 2006
 * 
 * Copyright (C) 2006 Idega Software hf. All Rights Reserved.
 * 
 * This software is the proprietary information of Idega hf. Use is subject to
 * license terms.
 */
package com.idega.eclipse.ejbwizards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public abstract class BeanCreator {

	private IType type;
	private Map importMap;
	
	private Set interfaceImports;
	private Set homeInterfaceImports;
	private Set homeImplImports;
	
	protected boolean isLegacyEntity = false;
	protected boolean isSessionBean = false;

	protected BeanCreator(IResource resource) {
		this(resource, false);
	}
	
	protected BeanCreator(IResource resource, boolean isLegacyOrSessionBean) {
		this.isLegacyEntity = isLegacyOrSessionBean;
		this.isSessionBean = isLegacyOrSessionBean;
		
		String resourceName = resource.getName();

		IJavaElement javaElement = JavaCore.create(resource);
		if (javaElement instanceof ICompilationUnit) {
			String typeName = resourceName.substring(0, resourceName.indexOf(".java"));
			ICompilationUnit compilationUnit = (ICompilationUnit) javaElement;
			fillImportMap(compilationUnit);
			this.type = compilationUnit.getType(typeName);
		}

		try {
			generateCode();
		}
		catch (JavaModelException jme) {
			jme.printStackTrace(System.err);
		}
	}
	
	protected void addInterfaceImport(String importName) {
		if (this.interfaceImports == null) {
			this.interfaceImports = new HashSet();
		}
		
		this.interfaceImports.add(importName);
	}
	
	protected Set getInterfaceImports() {
		if (this.interfaceImports == null) {
			this.interfaceImports = new HashSet();
		}
		
		return this.interfaceImports;
	}

	protected void addHomeInterfaceImport(String importName) {
		if (this.homeInterfaceImports == null) {
			this.homeInterfaceImports = new HashSet();
		}
		
		this.homeInterfaceImports.add(importName);
	}

	protected Set getHomeInterfaceImports() {
		if (this.homeInterfaceImports == null) {
			this.homeInterfaceImports = new HashSet();
		}
		
		return this.homeInterfaceImports;
	}

	protected void addHomeImplImport(String importName) {
		if (this.homeImplImports == null) {
			this.homeImplImports = new HashSet();
		}
		
		this.homeImplImports.add(importName);
	}

	protected Set getHomeImplImports() {
		if (this.homeImplImports == null) {
			this.homeImplImports = new HashSet();
		}
		
		return this.homeImplImports;
	}

	protected String getTypeComment(ICompilationUnit iUnit, String typeName) {
		try {
			String lineDelimiter = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			
			String comment = CodeGeneration.getTypeComment(iUnit, typeName, lineDelimiter);
			if (comment != null) {
				return comment;
			}
		}
		catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected String getFileComment(ICompilationUnit iUnit) {
		try {
			String lineDelimiter = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			
			String comment = CodeGeneration.getFileComment(iUnit, lineDelimiter);
			if (comment != null) {
				return comment;
			}
		}
		catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected abstract void generateCode() throws JavaModelException;

	protected IType getType() {
		return this.type;
	}

	protected String getImportSignature(String importName) {
		if (importName.indexOf("[") != -1) {
			importName = importName.substring(0, importName.indexOf("["));
		}
		return (String) this.importMap.get(importName);
	}

	protected void addInterfaceToBean(IProgressMonitor monitor, ICompilationUnit iUnit, String interfaceName) throws JavaModelException, MalformedTreeException, BadLocationException {
		String source = iUnit.getBuffer().getContents();
		Document document = new Document(source);

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(iUnit);

		CompilationUnit unit = (CompilationUnit) parser.createAST(monitor);
		unit.recordModifications();

		AST ast = unit.getAST();

		Type interfaceType = ast.newSimpleType(ast.newSimpleName(interfaceName));
		TypeDeclaration typeDeclaration = (TypeDeclaration) unit.types().get(0);
		if (typeDeclaration.superInterfaceTypes().contains(interfaceType)) {
			typeDeclaration.superInterfaceTypes().add(interfaceType);
		}
		
		TextEdit edits = unit.rewrite(document, iUnit.getJavaProject().getOptions(true));

		// computation of the new source code
		edits.apply(document);
		String newSource = document.get();

		// update of the compilation unit
		iUnit.getBuffer().setContents(newSource);

		iUnit.reconcile(ICompilationUnit.NO_AST, false, null, null);
		iUnit.commitWorkingCopy(true, null);
		iUnit.discardWorkingCopy();
	}
	
	private void fillImportMap(ICompilationUnit iUnit) {
		if (this.importMap == null) {
			this.importMap = new HashMap();
		}

		try {
			IImportDeclaration[] imports = iUnit.getImports();
			for (int i = 0; i < imports.length; i++) {
				IImportDeclaration declaration = imports[i];
				String importName = declaration.getElementName();
				this.importMap.put(importName.substring(importName.lastIndexOf(".") + 1), importName);
			}
		}
		catch (JavaModelException jme) {
			jme.printStackTrace();
		}
	}

	public List filterMethods(IMethod[] methods, MethodFilter[] validFilterNames, MethodFilter[] nonValidFilterNames) throws JavaModelException {
		ArrayList list = new ArrayList();

		for (int i = 0; i < methods.length; i++) {
			IMethod method = methods[i];

			if (Flags.isPublic(method.getFlags()) && !Flags.isStatic(method.getFlags())) {
				String methodName = method.getElementName();
				boolean valid = false;

				if (validFilterNames != null || nonValidFilterNames != null) {
					if (nonValidFilterNames != null) {
						valid = true;
						for (int j = 0; j < nonValidFilterNames.length; j++) {
							MethodFilter filter = nonValidFilterNames[j];
							if (filter.filter(methodName)) {
								valid &= false;
							}
						}
					}

					if (validFilterNames != null) {
						for (int j = 0; j < validFilterNames.length; j++) {
							boolean filters = validFilterNames[j].filter(methodName);
							if (filters) {
								valid |= true;
							}
						}
					}
				}
				else {
					valid = true;
				}
				if (valid) {
					list.add(method);
				}
			}
		}
		return list;
	}
	
	protected String getReturnType(String returnType) {
		returnType = Signature.getSignatureSimpleName(returnType);
		if (returnType.equals("void")) {
			returnType = null;
		}
		
		return returnType;
	}
	
	protected Type getType(AST ast, String type) {
		Type returnType = null;
		
		if (type != null) {
			boolean isArray = false;
			if (type.indexOf("[") != -1) {
				isArray = true;
			}
			
			try {
				if (isArray) {
					returnType = ast.newArrayType(ast.newSimpleType(ast.newSimpleName(type.substring(0, type.indexOf("[")))));
				}
				else {
					returnType = ast.newSimpleType(ast.newSimpleName(type));
				}
			}
			catch (IllegalArgumentException iae) {
				if (isArray) {
					returnType = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.toCode(type.substring(0, type.indexOf("[")))));
				}
				else {
					returnType = ast.newPrimitiveType(PrimitiveType.toCode(type));
				}
			}
		}
		else {
			returnType = ast.newPrimitiveType(PrimitiveType.VOID);
		}

		return returnType;
	}
	
	protected PackageDeclaration getPackageDeclaration(AST ast, String packageName) {
		PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		packageDeclaration.setName(ast.newName(packageName));
		
		return packageDeclaration;
	}
	
	protected TypeDeclaration getTypeDeclaration(AST ast, String name, boolean isInterface, String superClass, String[] interfaces, Set imports) {
		TypeDeclaration classType = ast.newTypeDeclaration();
		classType.setInterface(isInterface);
		classType.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		classType.setName(ast.newSimpleName(name));
		if (isInterface) {
			classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName(superClass)));
		}
		else {
			classType.setSuperclassType(ast.newSimpleType(ast.newSimpleName(superClass)));
		}

		if (interfaces != null) {
			for (int i = 0; i < interfaces.length; i++) {
				if (!Signature.getSignatureSimpleName(interfaces[i]).equals(name)) {
					classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName(Signature.getSignatureSimpleName(interfaces[i]))));
					imports.add(getImportSignature(Signature.toString(interfaces[i])));
				}
			}
		}
		
		return classType;
	}
	
	protected Javadoc getJavadoc(AST ast, IMethod method) {
		Javadoc jc = ast.newJavadoc();
		TagElement tag = ast.newTagElement();
		tag.setTagName(TagElement.TAG_SEE);
		TextElement te = ast.newTextElement();
		te.setText(getType().getFullyQualifiedName() + "#" + method.getElementName());
		tag.fragments().add(te);
		jc.tags().add(tag);
		
		return jc;
	}
	
	protected MethodDeclaration getMethodDeclaration(AST ast, IMethod method, Set imports) throws JavaModelException {
		String returnType = getReturnType(method.getReturnType());
		String methodName = method.getElementName();
		
		return getMethodDeclaration(ast, method, methodName, returnType, imports, true);
	}
	
	protected MethodDeclaration getMethodDeclaration(AST ast, IMethod method, String methodName, String returnType, Set imports, boolean addJavadoc) throws JavaModelException {
		String[] exceptions = method.getExceptionTypes();
		String[] parameterTypes = method.getParameterTypes();
		String[] parameterNames = method.getParameterNames();

		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(getType(ast, returnType));
		methodConstructor.setName(ast.newSimpleName(methodName));
		if (returnType != null) {
			imports.add(getImportSignature(returnType));
		}
		
		for (int i = 0; i < exceptions.length; i++) {
			methodConstructor.thrownExceptions().add(ast.newSimpleName(Signature.getSignatureSimpleName(exceptions[i])));
			imports.add(getImportSignature(Signature.toString(exceptions[i])));
		}

		for (int i = 0; i < parameterTypes.length; i++) {
			String parameterType = getReturnType(parameterTypes[i]);
			
			SingleVariableDeclaration variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(getType(ast, parameterType));
			variableDeclaration.setName(ast.newSimpleName(parameterNames[i]));
			methodConstructor.parameters().add(variableDeclaration);

			imports.add(getImportSignature(Signature.toString(parameterTypes[i])));
		}

		if (addJavadoc) {
			methodConstructor.setJavadoc(getJavadoc(ast, method));
		}
		
		return methodConstructor;
	}
	
	protected void writeImports(AST ast, CompilationUnit unit, Set imports) {
		Iterator iter = imports.iterator();
		while (iter.hasNext()) {
			String importName = (String) iter.next();

			if (importName != null) {
				ImportDeclaration importDeclaration = ast.newImportDeclaration();
				importDeclaration.setName(ast.newName(importName));
				importDeclaration.setOnDemand(false);
				
				unit.imports().add(importDeclaration);
			}
		}
	}
	
	protected void commitChanges(ICompilationUnit iUnit, CompilationUnit unit, Document document) throws MalformedTreeException, BadLocationException, JavaModelException {
		TextEdit edits = unit.rewrite(document, iUnit.getJavaProject().getOptions(true));
		edits.apply(document);

		String newSource = document.get();
		iUnit.getBuffer().setContents(newSource);

		iUnit.reconcile(ICompilationUnit.NO_AST, false, null, null);
		iUnit.commitWorkingCopy(true, null);
		iUnit.discardWorkingCopy();
	}
}