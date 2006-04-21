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
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class IDOEntityCreator extends BeanCreator {

	private boolean isLegacyEntity = false;

	public IDOEntityCreator(IResource resource) {
		this(resource, false);
	}

	public IDOEntityCreator(IResource resource, boolean isLegacyEntity) {
		super(resource);
		this.isLegacyEntity = isLegacyEntity;
	}

	protected void generateCode() throws JavaModelException {
		IProgressMonitor monitor = new NullProgressMonitor();
		monitor.beginTask("Begin creation", IProgressMonitor.UNKNOWN); //$NON-NLS-1$

		IResource idoEntityResource = getType().getResource();
		IPackageFragment pack = getType().getPackageFragment();
		ICompilationUnit unit = getType().getCompilationUnit();

		String typeName = idoEntityResource.getName().substring(0, idoEntityResource.getName().lastIndexOf("BMPBean"));
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
		if (!this.isLegacyEntity) {
			classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName("IDOEntity")));
			imports.add("com.idega.data.IDOEntity");
		}
		else {
			classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName("IDOLegacyEntity")));
			imports.add("com.idega.data.IDOLegacyEntity");
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
		classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName("IDOHome")));
		imports.add("com.idega.data.IDOHome");
		unit.types().add(classType);

		// create() method
		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
		methodConstructor.setName(ast.newSimpleName("create"));
		methodConstructor.thrownExceptions().add(ast.newName("CreateException"));
		imports.add("javax.ejb.CreateException");
		classType.bodyDeclarations().add(methodConstructor);

		// findByPrimarKey(Object) method
		methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
		methodConstructor.setName(ast.newSimpleName("findByPrimaryKey"));
		methodConstructor.thrownExceptions().add(ast.newName("FinderException"));
		imports.add("javax.ejb.FinderException");
		classType.bodyDeclarations().add(methodConstructor);

		SingleVariableDeclaration variableDeclaration = ast.newSingleVariableDeclaration();
		variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("Object")));
		variableDeclaration.setName(ast.newSimpleName("pk"));
		methodConstructor.parameters().add(variableDeclaration);

		if (this.isLegacyEntity) {
			// findByPrimarKey(int) method
			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
			methodConstructor.setName(ast.newSimpleName("findByPrimaryKey"));
			methodConstructor.thrownExceptions().add(ast.newName("javax.ejb.FinderException"));
			classType.bodyDeclarations().add(methodConstructor);

			variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(ast.newPrimitiveType(PrimitiveType.INT));
			variableDeclaration.setName(ast.newSimpleName("id"));
			methodConstructor.parameters().add(variableDeclaration);

			// findByPrimarKeyLegacy(int) method
			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
			methodConstructor.setName(ast.newSimpleName("findByPrimaryKeyLegacy"));
			methodConstructor.thrownExceptions().add(ast.newName("java.sql.SQLException"));
			imports.add("java.sql.SQLException");
			classType.bodyDeclarations().add(methodConstructor);

			variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(ast.newPrimitiveType(PrimitiveType.INT));
			variableDeclaration.setName(ast.newSimpleName("id"));
			methodConstructor.parameters().add(variableDeclaration);
		}

		MethodFilter[] validFilter = { new MethodFilter(WizardConstants.EJB_CREATE_START, MethodFilter.TYPE_PREFIX), new MethodFilter(WizardConstants.EJB_HOME_START, MethodFilter.TYPE_PREFIX), new MethodFilter(WizardConstants.EJB_FIND_START, MethodFilter.TYPE_PREFIX) };
		List methods = filterMethods(getType().getMethods(), validFilter, null);
		for (Iterator iter = methods.iterator(); iter.hasNext();) {
			IMethod method = (IMethod) iter.next();
			String fullMethodName = method.getElementName();
			String methodName = cutAwayEJBSuffix(fullMethodName);
			String[] exceptions = method.getExceptionTypes();
			String[] parameterTypes = method.getParameterTypes();
			String[] parameterNames = method.getParameterNames();
			String returnType = Signature.getSignatureSimpleName(method.getReturnType());
			if (returnType.equals("void")) {
				returnType = null;
			}
			boolean isPrimitive = false;

			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			if (fullMethodName.startsWith(WizardConstants.EJB_FIND_START) || fullMethodName.startsWith(WizardConstants.EJB_CREATE_START)) {
				if (!Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Collection")) && !Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Set"))) {
					returnType = name;
				}
			}
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
			methodConstructor.setName(ast.newSimpleName(methodName));
			classType.bodyDeclarations().add(methodConstructor);
			if (returnType != null && !isPrimitive) {
				imports.add(getImportSignature(Signature.toString(method.getReturnType())));
			}
			
			for (int i = 0; i < exceptions.length; i++) {
				methodConstructor.thrownExceptions().add(ast.newName(Signature.getSignatureSimpleName(exceptions[i])));
				imports.add(getImportSignature(Signature.toString(exceptions[i])));
			}

			for (int i = 0; i < parameterTypes.length; i++) {
				variableDeclaration = ast.newSingleVariableDeclaration();
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
		classType.setSuperclassType(ast.newSimpleType(ast.newSimpleName("IDOFactory")));
		classType.superInterfaceTypes().add(ast.newSimpleType(ast.newSimpleName(name + "Home")));
		imports.add("com.idega.data.IDOFactory");
		unit.types().add(classType);

		// create() method
		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName("Class")));
		methodConstructor.setName(ast.newSimpleName("getEntityInterfaceClass"));
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
		superMethodInvocation.setName(ast.newSimpleName("createIDO"));

		CastExpression ce = ast.newCastExpression();
		ce.setType(ast.newSimpleType(ast.newSimpleName(name)));
		ce.setExpression(superMethodInvocation);

		returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(ce);
		constructorBlock.statements().add(returnStatement);

		// findByPrimarKey(Object) method
		methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
		methodConstructor.setName(ast.newSimpleName("findByPrimaryKey"));
		methodConstructor.thrownExceptions().add(ast.newName("FinderException"));
		imports.add("javax.ejb.FinderException");
		classType.bodyDeclarations().add(methodConstructor);

		SingleVariableDeclaration variableDeclaration = ast.newSingleVariableDeclaration();
		variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("Object")));
		variableDeclaration.setName(ast.newSimpleName("pk"));
		methodConstructor.parameters().add(variableDeclaration);

		constructorBlock = ast.newBlock();
		methodConstructor.setBody(constructorBlock);

		superMethodInvocation = ast.newSuperMethodInvocation();
		superMethodInvocation.setName(ast.newSimpleName("findByPrimaryKeyIDO"));
		superMethodInvocation.arguments().add(ast.newSimpleName("pk"));

		ce = ast.newCastExpression();
		ce.setType(ast.newSimpleType(ast.newSimpleName(name)));
		ce.setExpression(superMethodInvocation);

		returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(ce);
		constructorBlock.statements().add(returnStatement);

		if (this.isLegacyEntity) {
			// createLegacy() method
			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
			methodConstructor.setName(ast.newSimpleName("createLegacy"));
			methodConstructor.thrownExceptions().add(ast.newName("CreateException"));
			classType.bodyDeclarations().add(methodConstructor);

			constructorBlock = ast.newBlock();
			methodConstructor.setBody(constructorBlock);

			TryStatement tryStatement = ast.newTryStatement();
			constructorBlock.statements().add(tryStatement);
			Block tryBlock = ast.newBlock();
			tryStatement.setBody(tryBlock);

			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("create"));

			returnStatement = ast.newReturnStatement();
			returnStatement.setExpression(mi);
			tryBlock.statements().add(returnStatement);

			CatchClause catchClause = ast.newCatchClause();
			tryStatement.catchClauses().add(catchClause);
			variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName(("CreateException"))));
			variableDeclaration.setName(ast.newSimpleName("ce"));
			catchClause.setException(variableDeclaration);
			Block catchBlock = ast.newBlock();
			catchClause.setBody(catchBlock);

			ClassInstanceCreation cc = ast.newClassInstanceCreation();
			cc.setType(ast.newSimpleType(ast.newSimpleName("RuntimeException")));
			mi = ast.newMethodInvocation();
			mi.setExpression(ast.newSimpleName("ce"));
			mi.setName(ast.newSimpleName("getMessage"));
			catchBlock.statements().add(ast.newExpressionStatement(mi));
			cc.arguments().add(mi);

			ThrowStatement throwStatement = ast.newThrowStatement();
			throwStatement.setExpression(cc);
			catchBlock.statements().add(throwStatement);

			// findByPrimarKey(int) method
			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
			methodConstructor.setName(ast.newSimpleName("findByPrimaryKey"));
			methodConstructor.thrownExceptions().add(ast.newName("FinderException"));
			classType.bodyDeclarations().add(methodConstructor);

			variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(ast.newPrimitiveType(PrimitiveType.INT));
			variableDeclaration.setName(ast.newSimpleName("id"));
			methodConstructor.parameters().add(variableDeclaration);

			constructorBlock = ast.newBlock();
			methodConstructor.setBody(constructorBlock);

			superMethodInvocation = ast.newSuperMethodInvocation();
			superMethodInvocation.setName(ast.newSimpleName("findByPrimaryKeyIDO"));
			superMethodInvocation.arguments().add(ast.newSimpleName("id"));

			returnStatement = ast.newReturnStatement();
			returnStatement.setExpression(superMethodInvocation);
			constructorBlock.statements().add(returnStatement);

			// findByPrimarKeyLegacy(int) method
			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
			methodConstructor.setName(ast.newSimpleName("findByPrimaryKeyLegacy"));
			methodConstructor.thrownExceptions().add(ast.newName("SQLException"));
			imports.add("java.sql.SQLException");
			classType.bodyDeclarations().add(methodConstructor);

			variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(ast.newPrimitiveType(PrimitiveType.INT));
			variableDeclaration.setName(ast.newSimpleName("id"));
			methodConstructor.parameters().add(variableDeclaration);

			constructorBlock = ast.newBlock();
			methodConstructor.setBody(constructorBlock);

			tryStatement = ast.newTryStatement();
			constructorBlock.statements().add(tryStatement);
			tryBlock = ast.newBlock();
			tryStatement.setBody(tryBlock);

			mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("findByPrimaryKey"));
			mi.arguments().add(ast.newSimpleName("id"));

			returnStatement = ast.newReturnStatement();
			returnStatement.setExpression(mi);
			tryBlock.statements().add(returnStatement);

			catchClause = ast.newCatchClause();
			tryStatement.catchClauses().add(catchClause);
			variableDeclaration = ast.newSingleVariableDeclaration();
			variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
			variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName(("FinderException"))));
			variableDeclaration.setName(ast.newSimpleName("fe"));
			catchClause.setException(variableDeclaration);
			catchBlock = ast.newBlock();
			catchClause.setBody(catchBlock);

			cc = ast.newClassInstanceCreation();
			cc.setType(ast.newSimpleType(ast.newSimpleName("SQLException")));
			mi = ast.newMethodInvocation();
			mi.setExpression(ast.newSimpleName("fe"));
			mi.setName(ast.newSimpleName("getMessage"));
			catchBlock.statements().add(ast.newExpressionStatement(mi));
			cc.arguments().add(mi);

			throwStatement = ast.newThrowStatement();
			throwStatement.setExpression(cc);
			catchBlock.statements().add(throwStatement);
		}

		MethodFilter[] validFilter = { new MethodFilter(WizardConstants.EJB_CREATE_START, MethodFilter.TYPE_PREFIX), new MethodFilter(WizardConstants.EJB_HOME_START, MethodFilter.TYPE_PREFIX), new MethodFilter(WizardConstants.EJB_FIND_START, MethodFilter.TYPE_PREFIX) };
		List methods = filterMethods(getType().getMethods(), validFilter, null);
		for (Iterator iter = methods.iterator(); iter.hasNext();) {
			IMethod method = (IMethod) iter.next();
			String fullMethodName = method.getElementName();
			String methodName = cutAwayEJBSuffix(fullMethodName);
			String[] exceptions = method.getExceptionTypes();
			String[] parameterTypes = method.getParameterTypes();
			String[] parameterNames = method.getParameterNames();
			String returnType = Signature.getSignatureSimpleName(method.getReturnType());
			if (returnType.equals("void")) {
				returnType = null;
			}
			boolean isPrimitive = false;

			methodConstructor = ast.newMethodDeclaration();
			methodConstructor.setConstructor(false);
			methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
			if (fullMethodName.startsWith(WizardConstants.EJB_FIND_START) || fullMethodName.startsWith(WizardConstants.EJB_CREATE_START)) {
				if (!Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Collection")) && !Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Set"))) {
					returnType = name;
				}
			}
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
			methodConstructor.setName(ast.newSimpleName(methodName));
			classType.bodyDeclarations().add(methodConstructor);
			if (returnType != null && !isPrimitive) {
				imports.add(getImportSignature(Signature.toString(method.getReturnType())));
			}
			
			for (int i = 0; i < exceptions.length; i++) {
				methodConstructor.thrownExceptions().add(ast.newName(Signature.getSignatureSimpleName(exceptions[i])));
				imports.add(getImportSignature(Signature.toString(exceptions[i])));
			}

			for (int i = 0; i < parameterTypes.length; i++) {
				variableDeclaration = ast.newSingleVariableDeclaration();
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
			
			constructorBlock = ast.newBlock();
			methodConstructor.setBody(constructorBlock);

			constructorBlock.statements().add(getIDOCheckOutStatement(ast, imports)); 

			if (fullMethodName.startsWith(WizardConstants.EJB_FIND_START)) {
				if (Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Collection"))) {
					constructorBlock.statements().add(getDataCollectingStatement(ast, returnType, "ids", fullMethodName, parameterNames));
					constructorBlock.statements().add(getIDOCheckInStatement(ast));
					constructorBlock.statements().add(getObjectReturnStatement(ast, "getEntityCollectionForPrimaryKeys", "ids"));
				}
				else if (Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Set"))) {
					constructorBlock.statements().add(getDataCollectingStatement(ast, returnType, "ids", fullMethodName, parameterNames));
					constructorBlock.statements().add(getIDOCheckInStatement(ast));
					constructorBlock.statements().add(getObjectReturnStatement(ast, "getEntitySetForPrimaryKeys", "ids"));
				}
				else {
					constructorBlock.statements().add(getDataCollectingStatement(ast, "Object", "pk", fullMethodName, parameterNames));
					constructorBlock.statements().add(getIDOCheckInStatement(ast));
					constructorBlock.statements().add(getObjectReturnStatement(ast, "findByPrimaryKey", "pk"));
				}
			}
			else if (fullMethodName.startsWith(WizardConstants.EJB_HOME_START)) {
				constructorBlock.statements().add(getDataCollectingStatement(ast, returnType, "theReturn", fullMethodName, parameterNames));
				constructorBlock.statements().add(getIDOCheckInStatement(ast));
				constructorBlock.statements().add(getPrimitiveReturnStatement(ast, "theReturn"));
			}
			else if (fullMethodName.startsWith(WizardConstants.EJB_CREATE_START)) {
				constructorBlock.statements().add(getDataCollectingStatement(ast, "Object", "pk", fullMethodName, parameterNames));
				
				ce = ast.newCastExpression();
				ce.setType(ast.newSimpleType(ast.newSimpleName(getType().getTypeQualifiedName())));
				ce.setExpression(ast.newSimpleName("entity"));
				
				ParenthesizedExpression pe = ast.newParenthesizedExpression(); 
				pe.setExpression(ce);
				MethodInvocation mi = ast.newMethodInvocation();
				mi.setExpression(pe);
				mi.setName(ast.newSimpleName("ejbPostCreate"));
				constructorBlock.statements().add(ast.newExpressionStatement(mi));
				
				constructorBlock.statements().add(getIDOCheckInStatement(ast));

				TryStatement tryStatement = ast.newTryStatement();
				constructorBlock.statements().add(tryStatement);
				Block tryBlock = ast.newBlock();
				tryStatement.setBody(tryBlock);

				mi = ast.newMethodInvocation();
				mi.setName(ast.newSimpleName("findByPrimaryKey"));
				mi.arguments().add(ast.newSimpleName("pk"));

				returnStatement = ast.newReturnStatement();
				returnStatement.setExpression(mi);
				tryBlock.statements().add(returnStatement);

				CatchClause catchClause = ast.newCatchClause();
				tryStatement.catchClauses().add(catchClause);
				variableDeclaration = ast.newSingleVariableDeclaration();
				variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
				variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName(("FinderException"))));
				variableDeclaration.setName(ast.newSimpleName("fe"));
				catchClause.setException(variableDeclaration);
				Block catchBlock = ast.newBlock();
				catchClause.setBody(catchBlock);

				ClassInstanceCreation cc = ast.newClassInstanceCreation();
				cc.setType(ast.newSimpleType(ast.newSimpleName("IDOCreateException")));
				imports.add("com.idega.data.IDOCreateException");
				cc.arguments().add(ast.newSimpleName(("fe")));

				ThrowStatement throwStatement = ast.newThrowStatement();
				throwStatement.setExpression(cc);
				catchBlock.statements().add(throwStatement);

				catchClause = ast.newCatchClause();
				tryStatement.catchClauses().add(catchClause);
				variableDeclaration = ast.newSingleVariableDeclaration();
				variableDeclaration.modifiers().addAll(ast.newModifiers(Modifier.NONE));
				variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName(("Exception"))));
				variableDeclaration.setName(ast.newSimpleName("e"));
				catchClause.setException(variableDeclaration);
				catchBlock = ast.newBlock();
				catchClause.setBody(catchBlock);

				cc = ast.newClassInstanceCreation();
				cc.setType(ast.newSimpleType(ast.newSimpleName("IDOCreateException")));
				cc.arguments().add(ast.newSimpleName(("e")));

				throwStatement = ast.newThrowStatement();
				throwStatement.setExpression(cc);
				catchBlock.statements().add(throwStatement);
			}
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
	
	private Statement getIDOCheckOutStatement(AST ast, Set imports) {
		VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
		vdf.setName(ast.newSimpleName("entity"));
		VariableDeclarationStatement vds = ast.newVariableDeclarationStatement(vdf);
		vds.setType(ast.newSimpleType(ast.newSimpleName("IDOEntity")));
		imports.add("com.idega.data.IDOEntity");

		ThisExpression thisExpression = ast.newThisExpression();
		MethodInvocation mi = ast.newMethodInvocation();
		mi.setExpression(thisExpression);
		mi.setName(ast.newSimpleName("idoCheckOutPooledEntity"));
		vdf.setInitializer(mi);
		
		return vds;
	}
	
	private Statement getIDOCheckInStatement(AST ast) {
		ThisExpression thisExpression = ast.newThisExpression();
		MethodInvocation mi = ast.newMethodInvocation();
		mi.setExpression(thisExpression);
		mi.setName(ast.newSimpleName("idoCheckInPooledEntity"));
		mi.arguments().add(ast.newSimpleName("entity"));
		
		return ast.newExpressionStatement(mi);
	}
	
	private Statement getDataCollectingStatement(AST ast, String returnType, String variableName, String methodName, String[] parameterNames) {
		VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
		vdf.setName(ast.newSimpleName(variableName));
		VariableDeclarationStatement vds = ast.newVariableDeclarationStatement(vdf);
		vds.setType(ast.newSimpleType(ast.newSimpleName(returnType)));
		
		CastExpression ce = ast.newCastExpression();
		ce.setType(ast.newSimpleType(ast.newSimpleName(getType().getTypeQualifiedName())));
		ce.setExpression(ast.newSimpleName("entity"));
		
		ParenthesizedExpression pe = ast.newParenthesizedExpression(); 
		pe.setExpression(ce);
		MethodInvocation mi = ast.newMethodInvocation();
		mi.setExpression(pe);
		mi.setName(ast.newSimpleName(methodName));
		vdf.setInitializer(mi);
		
		for (int i = 0; i < parameterNames.length; i++) {
			mi.arguments().add(ast.newSimpleName(parameterNames[i]));
		}
		
		return vds;
	}
	
	private Statement getObjectReturnStatement(AST ast, String methodName, String parameterName) {
		ThisExpression thisExpression = ast.newThisExpression();
		MethodInvocation mi = ast.newMethodInvocation();
		mi.setExpression(thisExpression);
		mi.setName(ast.newSimpleName(methodName));
		mi.arguments().add(ast.newSimpleName(parameterName));
		
		ReturnStatement returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(mi);
		
		return returnStatement;
	}

	private Statement getPrimitiveReturnStatement(AST ast, String variableName) {
		VariableDeclarationFragment vdf = ast.newVariableDeclarationFragment();
		vdf.setName(ast.newSimpleName(variableName));
		
		ReturnStatement returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(ast.newVariableDeclarationExpression(vdf));
		
		return returnStatement;
	}

	private String cutAwayEJBSuffix(String realMethodName) {
		String methodName = new String(realMethodName);
		if (realMethodName.startsWith(WizardConstants.EJB_FIND_START)) {
			methodName = "find" + realMethodName.substring(WizardConstants.EJB_FIND_START.length());
		}
		else if (realMethodName.startsWith(WizardConstants.EJB_HOME_START)) {
			String firstChar = realMethodName.substring(WizardConstants.EJB_HOME_START.length(), WizardConstants.EJB_HOME_START.length() + 1);
			methodName = firstChar.toLowerCase() + realMethodName.substring(WizardConstants.EJB_HOME_START.length() + 1, realMethodName.length());
		}
		else if (realMethodName.startsWith(WizardConstants.EJB_CREATE_START)) {
			methodName = "create" + realMethodName.substring(WizardConstants.EJB_CREATE_START.length());
		}
		return methodName;
	}
}