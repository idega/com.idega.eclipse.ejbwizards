/*
 * $Id$ Created on Apr 19,
 * 2006
 * 
 * Copyright (C) 2006 Idega Software hf. All Rights Reserved.
 * 
 * This software is the proprietary information of Idega hf. Use is subject to
 * license terms.
 */
package com.idega.eclipse.ejbwizards;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class IBOEntityCreator extends BeanCreator {

	private boolean isSessionBean = false;

	public IBOEntityCreator(IResource resource) {
		this(resource, false);
	}

	public IBOEntityCreator(IResource resource, boolean isSessionBean) {
		super(resource);
		this.isSessionBean = isSessionBean;
	}

	protected void generateCode() throws JavaModelException {
		IProgressMonitor monitor = new NullProgressMonitor();
		monitor.beginTask("Begin creation", IProgressMonitor.UNKNOWN); //$NON-NLS-1$

		IResource idoEntityResource = getType().getResource();
		IPackageFragment pack = getType().getPackageFragment();
		ICompilationUnit unit = getType().getCompilationUnit();

		String typeName = idoEntityResource.getName().substring(0, idoEntityResource.getName().lastIndexOf("Bean"));
		String[] interfaces = getType().getSuperInterfaceTypeSignatures();

		ICompilationUnit interfaceUnit = pack.createCompilationUnit(typeName + ".java", "", true, new SubProgressMonitor(monitor, 2)); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit interfaceHomeUnit = pack.createCompilationUnit(typeName + "Home.java", "", true, new SubProgressMonitor(monitor, 2)); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit homeImplUnit = pack.createCompilationUnit(typeName + "HomeImpl.java", "", true, new SubProgressMonitor(monitor, 2)); //$NON-NLS-1$ //$NON-NLS-2$

		try {
			createInterface(monitor, interfaceUnit.getWorkingCopy(monitor), pack.getElementName(), typeName, interfaces);
			createHomeInterface(monitor, interfaceHomeUnit.getWorkingCopy(monitor), pack.getElementName(), typeName);
			createHomeImplementation(monitor, homeImplUnit.getWorkingCopy(monitor), pack.getElementName(), typeName);
			addInterfaceToBean(monitor, unit.getWorkingCopy(monitor), typeName);
		}
		catch (MalformedTreeException e) {
			e.printStackTrace();
		}
		catch (BadLocationException e) {
			e.printStackTrace();
		}

		monitor.done();
	}

	private void createInterface(IProgressMonitor monitor, ICompilationUnit iUnit, String typePackage, String name, String[] interfaces) throws JavaModelException, MalformedTreeException, BadLocationException {
		String source = iUnit.getBuffer().getContents();
		Document document = new Document(source);

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(iUnit);

		CompilationUnit unit = (CompilationUnit) parser.createAST(monitor);
		unit.recordModifications();

		AST ast = unit.getAST();

		Set imports = new HashSet();

		// Package statement
		PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		unit.setPackage(packageDeclaration);
		packageDeclaration.setName(ast.newName(typePackage));

		// class declaration
		TypeDeclaration classType = ast.newTypeDeclaration();
		classType.setInterface(true);
		classType.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		classType.setName(ast.newSimpleName(name));
		if (!this.isSessionBean) {
			classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName("IBOService")));
			imports.add("com.idega.business.IBOService");
		}
		else {
			classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName("IBOSession")));
			imports.add("com.idega.business.IBOSession");
		}
		for (int i = 0; i < interfaces.length; i++) {
			if (!Signature.getSignatureSimpleName(interfaces[i]).equals(name)) {
				classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName(Signature.getSignatureSimpleName(interfaces[i]))));
				imports.add(getImportSignature(Signature.toString(interfaces[i])));
			}
		}
		unit.types().add(classType);

		MethodFilter[] nonValidFilter = { new MethodFilter(getType().getTypeQualifiedName(), MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.INITIALIZE_ATTRIBUTES, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.SET_DEFAULT_VALUES, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.INSERT_START_DATA, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.GET_ENTITY_NAME, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.UPDATE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.DELETE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.INSERT, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.REMOVE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.GET_NAME_OF_MIDDLE_TABLE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.GET_ID_COLUMN_NAME, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.EJB_START, MethodFilter.TYPE_PREFIX) };

		List methods = filterMethods(getType().getMethods(), null, nonValidFilter);
		for (Iterator iter = methods.iterator(); iter.hasNext();) {
			IMethod method = (IMethod) iter.next();
			String[] exceptions = method.getExceptionTypes();
			String[] parameterTypes = method.getParameterTypes();
			String[] parameterNames = method.getParameterNames();
			String returnType = Signature.getSignatureSimpleName(method.getReturnType());
			if (returnType.equals("void")) {
				returnType = null;
			}
			boolean isPrimitive = false;

			MethodDeclaration methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			if (returnType != null) {
				try {
					methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(returnType)));
				}
				catch (IllegalArgumentException iae) {
					methodConstructor.setReturnType2(ast.newPrimitiveType(PrimitiveType.toCode(returnType)));
					isPrimitive = true;
				}
			}
			else {
				methodConstructor.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
			}
			methodConstructor.setName(ast.newSimpleName(method.getElementName()));
			classType.bodyDeclarations().add(methodConstructor);
			if (returnType != null && !isPrimitive) {
				imports.add(getImportSignature(returnType));
			}
			
			for (int i = 0; i < exceptions.length; i++) {
				methodConstructor.thrownExceptions().add(ast.newSimpleName(Signature.getSignatureSimpleName(exceptions[i])));
				imports.add(getImportSignature(Signature.toString(exceptions[i])));
			}
			methodConstructor.thrownExceptions().add(ast.newSimpleName("RemoteException"));
			imports.add("java.rmi.RemoteException");

			for (int i = 0; i < parameterTypes.length; i++) {
				SingleVariableDeclaration variableDeclaration = ast.newSingleVariableDeclaration();
				variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
				try {
					variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName(Signature.getSignatureSimpleName(parameterTypes[i]))));
					isPrimitive = false;
				}
				catch (IllegalArgumentException iae) {
					variableDeclaration.setType(ast.newPrimitiveType(PrimitiveType.toCode(Signature.getSignatureSimpleName(parameterTypes[i]))));
					isPrimitive = true;
				}
				variableDeclaration.setName(ast.newSimpleName(parameterNames[i]));
				methodConstructor.parameters().add(variableDeclaration);

				if (!isPrimitive) {
					imports.add(getImportSignature(Signature.toString(parameterTypes[i])));
				}
			}

			Javadoc jc = ast.newJavadoc();
			TagElement tag = ast.newTagElement();
			tag.setTagName(TagElement.TAG_SEE);
			TextElement te = ast.newTextElement();
			te.setText(getType().getFullyQualifiedName() + "#" + method.getElementName());
			tag.fragments().add(te);
			jc.tags().add(tag);
			methodConstructor.setJavadoc(jc);
		}

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

		TextEdit edits = unit.rewrite(document, iUnit.getJavaProject().getOptions(true));
		edits.apply(document);

		String newSource = document.get();
		iUnit.getBuffer().setContents(newSource);

		iUnit.reconcile(ICompilationUnit.NO_AST, false, null, null);
		iUnit.commitWorkingCopy(true, null);
		iUnit.discardWorkingCopy();
	}

	private void createHomeInterface(IProgressMonitor monitor, ICompilationUnit iUnit, String typePackage, String name) throws JavaModelException, MalformedTreeException, BadLocationException {
		iUnit.getBuffer().setContents("");
		String source = iUnit.getBuffer().getContents();
		Document document = new Document(source);

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(iUnit);

		CompilationUnit unit = (CompilationUnit) parser.createAST(null);
		unit.recordModifications();

		AST ast = unit.getAST();

		Set imports = new HashSet();

		// Package statement
		PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		unit.setPackage(packageDeclaration);
		packageDeclaration.setName(ast.newName(typePackage));

		// class declaration
		TypeDeclaration classType = ast.newTypeDeclaration();
		classType.setInterface(true);
		classType.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		classType.setName(ast.newSimpleName(name + "Home"));
		classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName("IBOHome")));
		imports.add("com.idega.business.IBOHome");
		unit.types().add(classType);

		// create() method
		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
		methodConstructor.setName(ast.newSimpleName("create"));
		methodConstructor.thrownExceptions().add(ast.newName("CreateException"));
		imports.add("javax.ejb.CreateException");
		methodConstructor.thrownExceptions().add(ast.newName("RemoteException"));
		imports.add("java.rmi.RemoteException");
		classType.bodyDeclarations().add(methodConstructor);

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

		TextEdit edits = unit.rewrite(document, iUnit.getJavaProject().getOptions(true));
		edits.apply(document);

		String newSource = document.get();
		iUnit.getBuffer().setContents(newSource);

		iUnit.reconcile(ICompilationUnit.NO_AST, false, null, null);
		iUnit.commitWorkingCopy(true, null);
		iUnit.discardWorkingCopy();
	}

	private void createHomeImplementation(IProgressMonitor monitor, ICompilationUnit iUnit, String typePackage, String name) throws JavaModelException, MalformedTreeException, BadLocationException {
		iUnit.getBuffer().setContents("");
		String source = iUnit.getBuffer().getContents();
		Document document = new Document(source);

		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(iUnit);

		CompilationUnit unit = (CompilationUnit) parser.createAST(monitor);
		unit.recordModifications();

		AST ast = unit.getAST();

		Set imports = new HashSet();

		// Package statement
		PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		unit.setPackage(packageDeclaration);
		packageDeclaration.setName(ast.newName(typePackage));

		// class declaration
		TypeDeclaration classType = ast.newTypeDeclaration();
		classType.setInterface(false);
		classType.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		classType.setName(ast.newSimpleName(name + "HomeImpl"));
		classType.setSuperclassType(ast.newSimpleType(ast.newSimpleName("IBOHomeImpl")));
		classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName(name + "Home")));
		imports.add("com.idega.business.IBOHomeImpl");
		unit.types().add(classType);

		// create() method
		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName("Class")));
		methodConstructor.setName(ast.newSimpleName("getBeanInterfaceClass"));
		classType.bodyDeclarations().add(methodConstructor);

		Block constructorBlock = ast.newBlock();
		methodConstructor.setBody(constructorBlock);

		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newSimpleName(name)));

		ReturnStatement returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(typeLiteral);
		constructorBlock.statements().add(returnStatement);

		// create() method
		methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
		methodConstructor.setName(ast.newSimpleName("create"));
		methodConstructor.thrownExceptions().add(ast.newName("CreateException"));
		imports.add("javax.ejb.CreateException");
		classType.bodyDeclarations().add(methodConstructor);

		constructorBlock = ast.newBlock();
		methodConstructor.setBody(constructorBlock);

		SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
		superMethodInvocation.setName(ast.newSimpleName("createIBO"));

		CastExpression ce = ast.newCastExpression();
		ce.setType(ast.newSimpleType(ast.newSimpleName(name)));
		ce.setExpression(superMethodInvocation);

		returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(ce);
		constructorBlock.statements().add(returnStatement);

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

		TextEdit edits = unit.rewrite(document, iUnit.getJavaProject().getOptions(true));
		edits.apply(document);

		String newSource = document.get();
		iUnit.getBuffer().setContents(newSource);

		iUnit.reconcile(ICompilationUnit.NO_AST, false, null, null);
		iUnit.commitWorkingCopy(true, null);
		iUnit.discardWorkingCopy();
	}
}