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

import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;

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

		// Package statement
		unit.setPackage(getPackageDeclaration(ast, typePackage));

		// class declaration
		String superInterface = null;
		if (!this.isSessionBean) {
			superInterface = "IBOService";
			getInterfaceImports().add("com.idega.business.IBOService");
		}
		else {
			superInterface = "IBOSession";
			getInterfaceImports().add("com.idega.business.IBOSession");
		}

		TypeDeclaration classType = getTypeDeclaration(ast, name, true, superInterface, interfaces, getInterfaceImports());
		unit.types().add(classType);

		MethodFilter[] nonValidFilter = { new MethodFilter(getType().getTypeQualifiedName(), MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.INITIALIZE_ATTRIBUTES, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.SET_DEFAULT_VALUES, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.INSERT_START_DATA, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.GET_ENTITY_NAME, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.UPDATE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.DELETE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.INSERT, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.REMOVE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.GET_NAME_OF_MIDDLE_TABLE, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.GET_ID_COLUMN_NAME, MethodFilter.TYPE_WHOLE), new MethodFilter(WizardConstants.EJB_START, MethodFilter.TYPE_PREFIX) };

		List methods = filterMethods(getType().getMethods(), null, nonValidFilter);
		for (Iterator iter = methods.iterator(); iter.hasNext();) {
			IMethod method = (IMethod) iter.next();
			MethodDeclaration methodConstructor = getMethodDeclaration(ast, method, getInterfaceImports());
			classType.bodyDeclarations().add(methodConstructor);

			methodConstructor.thrownExceptions().add(ast.newSimpleName("RemoteException"));
			getInterfaceImports().add("java.rmi.RemoteException");
		}

		writeImports(ast, unit, getInterfaceImports());
		commitChanges(iUnit, unit, document);
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

		// Package statement
		unit.setPackage(getPackageDeclaration(ast, typePackage));

		// class declaration
		TypeDeclaration classType = getTypeDeclaration(ast, name + "Home", true, "IBOHome", null, getHomeInterfaceImports());
		getHomeInterfaceImports().add("com.idega.business.IBOHome");
		unit.types().add(classType);

		// create() method
		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(false);
		methodConstructor.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC));
		methodConstructor.setReturnType2(ast.newSimpleType(ast.newSimpleName(name)));
		methodConstructor.setName(ast.newSimpleName("create"));
		methodConstructor.thrownExceptions().add(ast.newName("CreateException"));
		getHomeInterfaceImports().add("javax.ejb.CreateException");
		methodConstructor.thrownExceptions().add(ast.newName("RemoteException"));
		getHomeInterfaceImports().add("java.rmi.RemoteException");
		classType.bodyDeclarations().add(methodConstructor);

		writeImports(ast, unit, getHomeInterfaceImports());
		commitChanges(iUnit, unit, document);
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

		// Package statement
		PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
		unit.setPackage(packageDeclaration);
		packageDeclaration.setName(ast.newName(typePackage));

		// class declaration
		TypeDeclaration classType = getTypeDeclaration(ast, name + "HomeImpl", false, "IBOHomeImpl", null, getHomeImplImports());
		getHomeImplImports().add("com.idega.business.IBOHomeImpl");
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
		getHomeImplImports().add("javax.ejb.CreateException");
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

		writeImports(ast, unit, getHomeImplImports());
		commitChanges(iUnit, unit, document);
	}
}