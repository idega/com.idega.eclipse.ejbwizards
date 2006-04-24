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
import java.util.List;
import java.util.Map;

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
import org.eclipse.jdt.core.dom.PrimitiveType;
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

	protected BeanCreator(IResource resource) {
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
				returnType = ast.newPrimitiveType(PrimitiveType.toCode(type));
			}
		}
		else {
			returnType = ast.newPrimitiveType(PrimitiveType.VOID);
		}

		return returnType;
	}
}