/*
 * Created on 5.7.2004
 *
 * Copyright (C) 2004 Idega hf. All Rights Reserved.
 *
 *  This software is the proprietary information of Idega hf.
 *  Use is subject to license terms.
 */
package com.idega.eclipse.ejbwizards;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.core.dom.rewrite.TokenScanner;
import org.eclipse.jdt.internal.corext.codemanipulation.IImportsStructure;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportsStructure;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * @author aron
 *
 * ResourceCreator creates interface and implementation source code for IDOEntity beans
 */
public class ResourceCreator {
	
	/** Public access flag. See The Java Virtual Machine Specification for more details. */
	public int F_PUBLIC = Flags.AccPublic;
	/** Private access flag. See The Java Virtual Machine Specification for more details. */
	public int F_PRIVATE = Flags.AccPrivate;
	/**  Protected access flag. See The Java Virtual Machine Specification for more details. */
	public int F_PROTECTED = Flags.AccProtected;
	/** Static access flag. See The Java Virtual Machine Specification for more details. */
	public int F_STATIC = Flags.AccStatic;
	/** Final access flag. See The Java Virtual Machine Specification for more details. */
	public int F_FINAL = Flags.AccFinal;
	/** Abstract property flag. See The Java Virtual Machine Specification for more details. */
	public int F_ABSTRACT = Flags.AccAbstract;
	
	private List fSuperInterfaces = new ArrayList();
	private List imports = new ArrayList();
	private List fRequiredExceptions = new ArrayList();
	private IImportDeclaration[] importDeclarations = null;
	private String fSuperClassName = "";
	
	private boolean fIsClass;
	private IType fSuperClass;
    private String typeName;
    private String remoteName;
    private String homeName;
    private boolean isEntity = false;
    private boolean isService = false;
    private boolean throwRemoteExcptionInHome = true;
	
	public void createResource(IFile file,int type){
		try {
            createResource((IResource)file,type);
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
	}
	
	public void createResource(IJavaElement javaElement,int type){
		try {
            IResource resource = javaElement.getResource();
            createResource(resource,type);
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
		
	}
	
	private void createResource(IResource resource,int type)throws JavaModelException{
		String resourceName = resource.getName();
		/*
		String location = resource.getLocation().toOSString();
		String workingDirectory = location.substring(0,location.indexOf(resourceName));
		System.out.println("resource name      : "+resourceName);
		System.out.println("working directory  : "+workingDirectory);
		*/
		
		IJavaElement javaElement = JavaCore.create(resource);
		IType itype = null;
		if(javaElement instanceof ICompilationUnit){
			String typeName = resourceName.substring(0,resourceName.indexOf(".java"));
			ICompilationUnit compilationUnit  = (ICompilationUnit) javaElement;
			importDeclarations = compilationUnit.getImports();
			itype = compilationUnit.getType(typeName);
		}
		String lineDelimiter= System.getProperty("line.separator", "\n");
		isEntity = type==WizardConstants.IDOENTITY || type==WizardConstants.IDOLEGACYENTITY;
		isService = type==WizardConstants.IBOSERVICE || type==WizardConstants.IBOSESSION;
		try {
		if(itype!=null){
		    IProgressMonitor monitor = new NullProgressMonitor();
			switch (type) {
			case WizardConstants.IDOENTITY:
			    // create remote interface
			    	String code = createRemoteMethods(itype,lineDelimiter,type).toString();
				setRemoteName(resourceName.substring(0,resourceName.lastIndexOf("BMPBean")));
				setTypeName(getRemoteName());
				addSuperInterface("com.idega.data.IDOEntity");
				
				fIsClass = false;
				createType(monitor,itype,code.toString());
				resetSuperInterfaces();
				
				// create home interface
				
				String homeName = remoteName+"Home";
				setTypeName(homeName);
				addSuperInterface("com.idega.data.IDOHome");
				setThrowRemoteExceptionInHome(false);
				code = createHomeMethods(itype,lineDelimiter,type).toString();
				fIsClass = false;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				// create home implementation
				String homeImplName = homeName+"Impl";
				setTypeName(homeImplName);
			
				setSuperClass("com.idega.data.IDOFactory");
				addSuperInterface(homeName);
				code = createHomeImplMethods(itype,lineDelimiter,type).toString();
				fIsClass = true;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				addInterfaceImplemenation(monitor,itype,getRemoteName());
				
				break;
			case WizardConstants.IDOLEGACYENTITY:
			    code = createRemoteMethods(itype,lineDelimiter,type).toString();
				setRemoteName(resourceName.substring(0,resourceName.lastIndexOf("BMPBean")));
				setTypeName(getRemoteName());
				
				addSuperInterface("com.idega.data.IDOLegacyEntity");
				fIsClass = false;
				createType(monitor,itype,code.toString());
				resetSuperInterfaces();
				
				// create home interface
				homeName = remoteName+"Home";
				setTypeName(homeName);
				setThrowRemoteExceptionInHome(false);
				addSuperInterface("com.idega.data.IDOHome");
				code = createHomeMethods(itype,lineDelimiter,type).toString();
				fIsClass = false;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				// create home implementation
				homeImplName = homeName+"Impl";
				setTypeName(homeImplName);
			
				setSuperClass("com.idega.data.IDOFactory");
				addSuperInterface(homeName);
				code = createHomeImplMethods(itype,lineDelimiter,type).toString();
				fIsClass = true;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				addInterfaceImplemenation(monitor,itype,getRemoteName());
				
				break;
			case WizardConstants.IBOSERVICE:
			    addRequiredException("java.rmi.RemoteException");
				code = createRemoteMethods(itype,lineDelimiter,type).toString();
				setRemoteName(resourceName.substring(0,resourceName.lastIndexOf("Bean")));
				setTypeName(getRemoteName());
				addSuperInterface("com.idega.business.IBOService");

				fIsClass = false;
				createType(monitor,itype,code.toString());
				resetSuperInterfaces();
				resetRequiredExceptions();
				// create home interface
				homeName = remoteName+"Home";
				setTypeName(homeName);
				setThrowRemoteExceptionInHome(true);
				addSuperInterface("com.idega.business.IBOHome");
				code = createHomeMethods(itype,lineDelimiter,type).toString();
				fIsClass = false;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				// create home implementation
				homeImplName = homeName+"Impl";
				setTypeName(homeImplName);
				setSuperClass("com.idega.business.IBOHomeImpl");
				addSuperInterface(homeName);
				code = createHomeImplMethods(itype,lineDelimiter,type).toString();
				fIsClass = true;
				createType(monitor,itype,code);
				resetSuperInterfaces();
			
				addInterfaceImplemenation(monitor,itype,getRemoteName());
				
				break;
			case WizardConstants.IBOSESSION:
			    addRequiredException("java.rmi.RemoteException");
			    code = createRemoteMethods(itype,lineDelimiter,type).toString();
				setRemoteName(resourceName.substring(0,resourceName.lastIndexOf("Bean")));
				setTypeName(getRemoteName());
				addSuperInterface("com.idega.business.IBOSession");
				
				fIsClass = false;
				createType(monitor,itype,code.toString());
				resetSuperInterfaces();
				resetRequiredExceptions();
				
				// create home interface
				homeName = remoteName+"Home";
				setTypeName(homeName);
				addSuperInterface("com.idega.business.IBOHome");
				setThrowRemoteExceptionInHome(true);
				code = createHomeMethods(itype,lineDelimiter,type).toString();
				fIsClass = false;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				// create home implementation
				homeImplName = homeName+"Impl";
				setTypeName(homeImplName);
				setSuperClass("com.idega.business.IBOHomeImpl");
				addSuperInterface(homeName);
				code = createHomeImplMethods(itype,lineDelimiter,type).toString();
				fIsClass = true;
				createType(monitor,itype,code);
				resetSuperInterfaces();
				
				addInterfaceImplemenation(monitor,itype,getRemoteName());
				
				break;
			}
			monitor.done();
		}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void createType(IProgressMonitor monitor,IType iType,String methodCode)throws CoreException, InterruptedException{
		
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}

		monitor.beginTask("Begin creation", 10); //$NON-NLS-1$
		ICompilationUnit createdWorkingCopy= null;
		try {
			
			String clName= getTypeName();
			IPackageFragment pack = iType.getPackageFragment();
			
			IType createdType;
			ImportsManager imports;
			int indent= 0;
			String lineDelimiter= System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			ICompilationUnit parentCU = null;
			if(!pack.exists())
			    parentCU= pack.createCompilationUnit(clName + ".java", "", false, new SubProgressMonitor(monitor, 2)); //$NON-NLS-1$ //$NON-NLS-2$
			else
			    parentCU = pack.getCompilationUnit(clName + ".java");
			// create a working copy with a new owner
			createdWorkingCopy= parentCU.getWorkingCopy(null);
			
			// use the compiler template a first time to read the imports
			String content= CodeGeneration.getCompilationUnitContent(createdWorkingCopy, null, "", lineDelimiter); //$NON-NLS-1$
			if (content != null) {
				createdWorkingCopy.getBuffer().setContents(content);
			}
			
			imports= new ImportsManager(createdWorkingCopy);
			// add an import that will be removed again. Having this import solves 14661
			//imports.addImport(JavaModelUtil.concatenateName(pack.getElementName(), clName));
			
			if(importDeclarations!=null){
			    for (int i = 0; i < importDeclarations.length; i++) {
			        imports.addImport(  importDeclarations[i].getElementName());
                } 
			}
			
			String typeContent= constructTypeStub(imports, lineDelimiter,methodCode);
			
			String cuContent= constructCUContent(parentCU, typeContent, lineDelimiter);
			
			createdWorkingCopy.getBuffer().setContents(cuContent);
			
			createdType= createdWorkingCopy.getType(clName);
			//createdWorkingCopy.commit(false,monitor);
			
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}
			
			// add imports for superclass/interfaces, so types can be resolved correctly
			
			ICompilationUnit cu= createdType.getCompilationUnit();	
			boolean needsSave= !cu.isWorkingCopy();
			
			imports.create(needsSave, new SubProgressMonitor(monitor, 1));
				
			JavaModelUtil.reconcile(cu);

			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}
			
			// set up again
			imports= new ImportsManager(imports.getCompilationUnit(), imports.getAddedTypes());
			
			//createTypeMembers(createdType, imports, new SubProgressMonitor(monitor, 1));
	
			// add imports
			imports.create(needsSave, new SubProgressMonitor(monitor, 1));
			
			removeUnusedImports(cu, imports.getAddedTypes(), needsSave);
			
			JavaModelUtil.reconcile(cu);
			
			ISourceRange range= createdType.getSourceRange();
			
			IBuffer buf= cu.getBuffer();
			String originalContent= buf.getText(range.getOffset(), range.getLength());
			
			String formattedContent= CodeFormatterUtil.format(CodeFormatter.K_CLASS_BODY_DECLARATIONS, originalContent, indent, null, lineDelimiter, pack.getJavaProject()); 
			buf.replace(range.getOffset(), range.getLength(), formattedContent);
			
			cu.commitWorkingCopy(false, new SubProgressMonitor(monitor, 1));
			
		} finally {
			if (createdWorkingCopy != null) {
				createdWorkingCopy.discardWorkingCopy();
			}
			monitor.done();
		}
		
	}
	
	private void addInterfaceImplemenation(IProgressMonitor monitor,IType iType,String interfaceName)throws JavaModelException, InterruptedException{
	    boolean hasAlreadyImplementedRemote = false;
		String[] interfaces = iType.getSuperInterfaceNames();
		
		for (int i = 0; i < interfaces.length; i++) {
		    if(interfaces[i].equals(interfaceName)){
		        hasAlreadyImplementedRemote = true;
		    }
		}
		
		if(!hasAlreadyImplementedRemote){
	    ICompilationUnit createdWorkingCopy= null;
		try {
			
			String clName= iType.getTypeQualifiedName();
			IPackageFragment pack = iType.getPackageFragment();
			
			IType createdType;
			ICompilationUnit parentCU = null;
			if(!pack.exists())
			    parentCU= pack.createCompilationUnit(clName + ".java", "", false, new SubProgressMonitor(monitor, 2)); //$NON-NLS-1$ //$NON-NLS-2$
			else
			    parentCU = pack.getCompilationUnit(clName + ".java");
			// create a working copy with a new owner
			createdWorkingCopy= parentCU.getWorkingCopy(null);
			
			
			createdType= createdWorkingCopy.getType(clName);
			//createdWorkingCopy.commit(false,monitor);
			
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}
			
			// add imports for superclass/interfaces, so types can be resolved correctly
			
			ICompilationUnit cu= createdType.getCompilationUnit();	
			
			ISourceRange range= createdType.getSourceRange();
			//ISourceRange importRange = parentCU.getImportContainer().getSourceRange();
			
			IBuffer buf= cu.getBuffer();
			//int offset = importRange.getOffset()+importRange.getLength();
			//int length = range.getOffset()-offset;
			ISourceRange nameRange = iType.getNameRange();
			int diff = nameRange.getOffset()-range.getOffset();
			
			String source = buf.getText(nameRange.getOffset(),range.getLength()-diff);
			int indexOfSvigi = source.indexOf("{");
			String header = source.substring(0,indexOfSvigi);
			int headerLength = header.length();
			
			
			    if(interfaces.length>0)
			        header += ", "+getRemoteName();
			    else
			        header += " implements "+interfaceName;
			    
			
			buf.replace(nameRange.getOffset(),headerLength,header);
			
			//String originalContent= buf.getText(nameRange.getOffset(), nameRange.getLength());
			
			
			//buf.replace(range.getOffset(), range.getLength(), "");
			
			cu.commitWorkingCopy(false, new SubProgressMonitor(monitor, 1));
		} finally {
			if (createdWorkingCopy != null) {
				createdWorkingCopy.discardWorkingCopy();
			}
			monitor.done();
		}
}
	}
	
	private void removeUnusedImports(ICompilationUnit cu, Set addedTypes, boolean needsSave) throws CoreException {
		ASTParser parser= ASTParser.newParser(AST.JLS2);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		IProblem[] problems= root.getProblems();
		ArrayList res= new ArrayList();
		for (int i= 0; i < problems.length; i++) {
			int id= problems[i].getID();
			if (id == IProblem.UnusedImport || id == IProblem.ImportNotVisible) { // not visibles hide unused -> remove both  	 
				String imp= problems[i].getArguments()[0];
				res.add(imp);
			}
		}
		if (!res.isEmpty()) {
			ImportsManager imports= new ImportsManager(cu, addedTypes);
			for (int i= 0; i < res.size(); i++) {
				String curr= (String) res.get(i);
				imports.removeImport(curr);
			}
			imports.create(needsSave, null);
		}
	}
	
	/*
	 * Called from createType to construct the source for this type
	 */		
	private String constructTypeStub(ImportsManager imports, String lineDelimiter,String methodCode) {	
		StringBuffer buf= new StringBuffer();
			
		int modifiers= getModifiers();
		buf.append(Flags.toString(modifiers));
		if (modifiers != 0) {
			buf.append(' ');
		}
		buf.append(fIsClass ? "class " : "interface "); //$NON-NLS-2$ //$NON-NLS-1$
		buf.append(getTypeName());
		writeSuperClass(buf, imports);
		writeSuperInterfaces(buf, imports);	
		buf.append('{');
		buf.append(lineDelimiter);
		buf.append(methodCode);
		buf.append(lineDelimiter);
		buf.append('}');
		buf.append(lineDelimiter);
		return buf.toString();
	}
	
	/**
     * @return
     */
    private StringBuffer getCode(int type,String lineDelimiter) {
       
        return null;
    }

    /**
	 * Returns the selected modifiers.
	 * 
	 * @return the selected modifiers
	 * @see Flags 
	 */	
	public int getModifiers() {
		int mdf= 0;
		mdf+= F_PUBLIC;
		/*
		if (fAccMdfButtons.isSelected(PUBLIC_INDEX)) {
			mdf+= F_PUBLIC;
		} else if (fAccMdfButtons.isSelected(PRIVATE_INDEX)) {
			mdf+= F_PRIVATE;
		} else if (fAccMdfButtons.isSelected(PROTECTED_INDEX)) {	
			mdf+= F_PROTECTED;
		}
		if (fOtherMdfButtons.isSelected(ABSTRACT_INDEX)) {	
			mdf+= F_ABSTRACT;
		}
		if (fOtherMdfButtons.isSelected(FINAL_INDEX)) {	
			mdf+= F_FINAL;
		}
		if (fOtherMdfButtons.isSelected(STATIC_INDEX)) {	
			mdf+= F_STATIC;
		}*/
		return mdf;
	}
	
	private void writeSuperClass(StringBuffer buf, ImportsManager imports) {
		String typename= getSuperClass();
		if (fIsClass && typename.length() > 0 && !"java.lang.Object".equals(typename)) { //$NON-NLS-1$
			buf.append(" extends "); //$NON-NLS-1$
			
			String qualifiedName= fSuperClass != null ? JavaModelUtil.getFullyQualifiedName(fSuperClass) : typename; 
			buf.append(imports.addImport(qualifiedName));
		}
	}
	
	private void writeSuperInterfaces(StringBuffer buf, ImportsManager imports) {
		List interfaces= getSuperInterfaces();
		int last= interfaces.size() - 1;
		if (last >= 0) {
			if (fIsClass) {
				buf.append(" implements "); //$NON-NLS-1$
			} else {
				buf.append(" extends "); //$NON-NLS-1$
			}
			for (int i= 0; i <= last; i++) {
				String typename= (String) interfaces.get(i);
				if(Signature.getQualifier(typename).length()>0){
				    buf.append(imports.addImport(typename));
				    
				}
				else{
				    buf.append(typename);
				}
				if (i < last) {
				    buf.append(',');
			    }	
			}
		}
	}
	
	
	protected String constructCUContent(ICompilationUnit cu, String typeContent, String lineDelimiter) throws CoreException {
		String typeComment= getTypeComment(cu, lineDelimiter);
		IPackageFragment pack= (IPackageFragment) cu.getParent();
		String content= CodeGeneration.getCompilationUnitContent(cu, typeComment, typeContent, lineDelimiter);
		if (content != null) {
			ASTParser parser= ASTParser.newParser(AST.JLS2);
			parser.setSource(content.toCharArray());
			CompilationUnit unit= (CompilationUnit) parser.createAST(null);
			if ((pack.isDefaultPackage() || unit.getPackage() != null) && !unit.types().isEmpty()) {
				return content;
			}
		}
		StringBuffer buf= new StringBuffer();
		if (!pack.isDefaultPackage()) {
			buf.append("package ").append(pack.getElementName()).append(';'); //$NON-NLS-1$
		}
		buf.append(lineDelimiter).append(lineDelimiter);
		if (typeComment != null) {
			buf.append(typeComment).append(lineDelimiter);
		}
		buf.append(typeContent);
		return buf.toString();
	}
	
	
	
	protected String getTypeComment(ICompilationUnit parentCU, String lineDelimiter) {
		try {
			StringBuffer typeName= new StringBuffer();
			//if (isEnclosingTypeSelected()) {
			//	typeName.append(JavaModelUtil.getTypeQualifiedName(getEnclosingType())).append('.');
			//}
			typeName.append(getTypeName());
			String comment= CodeGeneration.getTypeComment(parentCU, typeName.toString(), lineDelimiter);
			if (comment != null && isValidComment(comment)) {
				return comment;
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		return null;
	}
	
	private boolean isValidComment(String template) {
		IScanner scanner= ToolFactory.createScanner(true, false, false, false);
		scanner.setSource(template.toCharArray());
		try {
			int next= scanner.getNextToken();
			while (TokenScanner.isComment(next)) {
				next= scanner.getNextToken();
			}
			return next == ITerminalSymbols.TokenNameEOF;
		} catch (InvalidInputException e) {
		}
		return false;
	}
	


	/**
	 * @param type
	 * @return
	 */
	private String getTypeName() {
        return typeName;
	}
	
	private void setTypeName(String name){
	    typeName = name;
	}
	
	/**
	 * Returns the content of the superclass input field.
	 * 
	 * @return the superclass name
	 */
	public String getSuperClass() {
		return fSuperClassName;
	}

	/**
	 * Sets the super class name.
	 * 
	 * @param name the new superclass name
	 */		
	public void setSuperClass(String name) {
		fSuperClassName = name;
	}	
	
	/**
	 * Returns the chosen super interfaces.
	 * 
	 * @return a list of chosen super interfaces. The list's elements
	 * are of type <code>String</code>
	 */
	public List getSuperInterfaces() {
		return fSuperInterfaces;
	}

	/**
	 * Sets the super interfaces.
	 * 
	 * @param interfacesNames a list of super interface. The method requires that
	 * the list's elements are of type <code>String</code>
	 */	
	public void setSuperInterfaces(List interfacesNames) {
		fSuperInterfaces.addAll(interfacesNames);
	}
	
	public void resetSuperInterfaces(){
	    fSuperInterfaces.clear();
	}
	
	/**
	 * Adds a super interface
	 * @param name the new superinterface name
	 */
	public void addSuperInterface(String name){
	    fSuperInterfaces.add(name);
	}
	
	
	
	public StringBuffer createRemoteMethods(IType itype, String lineDelimiter,int type)throws JavaModelException {
	    StringBuffer methodsCode = new StringBuffer();
	    
	    MethodFilter[] nonValidFilter = {new MethodFilter(itype.getTypeQualifiedName(),MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.INITIALIZE_ATTRIBUTES,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.SET_DEFAULT_VALUES,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.INSERT_START_DATA,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.GET_ENTITY_NAME,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.UPDATE,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.DELETE,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.INSERT,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.REMOVE,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.GET_NAME_OF_MIDDLE_TABLE,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.GET_ID_COLUMN_NAME,MethodFilter.TYPE_WHOLE),
	            new MethodFilter(WizardConstants.EJB_START,MethodFilter.TYPE_PREFIX)
	    };
	    
	    List methods = filterMethods(itype.getMethods(),null,nonValidFilter);
	    for (Iterator iter = methods.iterator(); iter.hasNext();) {
            IMethod method = (IMethod) iter.next();
            methodsCode.append("/**").append(lineDelimiter);
            methodsCode.append(" * @see ").append(itype.getFullyQualifiedName()).append("#").append(method.getElementName()).append(lineDelimiter);
            methodsCode.append(" */").append(lineDelimiter);
            methodsCode.append((getMethodSignature(method).toString())).append(";").append(lineDelimiter);
            
        }
		return methodsCode;
	}
	
	/**
     * @return
     */
    private boolean throwRemoteExceptionsInHome() {
        return throwRemoteExcptionInHome;
    }
    
    private void setThrowRemoteExceptionInHome(boolean flag){
        throwRemoteExcptionInHome = flag;
    }
    

    public StringBuffer createHomeMethods(IType itype,String lineDelimiter,int type)throws JavaModelException{
	    StringBuffer methodsCode = new StringBuffer(); 
	    
	    methodsCode.append("public ").append( getRemoteName()).append( " create() throws javax.ejb.CreateException");
	    if (throwRemoteExceptionsInHome()) {
	        methodsCode.append(", java.rmi.RemoteException");
		}
	    methodsCode.append(";").append(lineDelimiter);
	    
	    if(isEntity){
		    methodsCode.append("public ").append(getRemoteName()).append( " findByPrimaryKey(Object pk) throws javax.ejb.FinderException");
			if (throwRemoteExceptionsInHome()) {
			    methodsCode.append(", java.rmi.RemoteException");
			}
			 methodsCode.append(";").append(lineDelimiter);
			 
			
			if (type==WizardConstants.IDOLEGACYENTITY) {
			    methodsCode.append(" public ").append(getRemoteName()).append(" findByPrimaryKey(int id) throws javax.ejb.FinderException");
				if (throwRemoteExceptionsInHome()) {
				    methodsCode.append(", java.rmi.RemoteException");
				}
				 methodsCode.append(";").append(lineDelimiter);
				methodsCode.append(" public ").append(getRemoteName()).append(" findByPrimaryKeyLegacy(int id) throws java.sql.SQLException");
				if (throwRemoteExceptionsInHome()) {
				    methodsCode.append(", java.rmi.RemoteException");
				}
				 methodsCode.append(";").append(lineDelimiter);
			}
	    }
	    
	    MethodFilter[] validFilter = {new MethodFilter(WizardConstants.EJB_CREATE_START,MethodFilter.TYPE_PREFIX),
	            new MethodFilter(WizardConstants.EJB_HOME_START,MethodFilter.TYPE_PREFIX),
	                    new MethodFilter(WizardConstants.EJB_FIND_START,MethodFilter.TYPE_PREFIX)};
	    MethodFilter[] validFilter2 = {new MethodFilter(WizardConstants.EJB_CREATE_START,MethodFilter.TYPE_PREFIX),
	            new MethodFilter(WizardConstants.EJB_HOME_START,MethodFilter.TYPE_PREFIX)};
	    if(isService)
	        validFilter = validFilter2;
	    List methods = filterMethods(itype.getMethods(),validFilter,null);
	    for (Iterator iter = methods.iterator(); iter.hasNext();) {
            IMethod method = (IMethod) iter.next();
            methodsCode.append("/**").append(lineDelimiter);
            methodsCode.append(" * @see ").append(itype.getFullyQualifiedName()).append("#").append(method.getElementName()).append(lineDelimiter);
            methodsCode.append(" */").append(lineDelimiter);
            String realMethodName = method.getElementName();
            if(realMethodName.startsWith(WizardConstants.EJB_FIND_START)){
                if (Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Collection"))) {
                    methodsCode.append((getMethodSignature(method).toString())).append(";").append(lineDelimiter);
    			   } 
                else if (Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Set"))) {
                    methodsCode.append((getMethodSignature(method).toString())).append(";").append(lineDelimiter);
                } 
                else {
    			    methodsCode.append((getMethodSignature(method,getRemoteName()).toString())).append(";").append(lineDelimiter);
                }
            }
            else if(realMethodName.startsWith(WizardConstants.EJB_CREATE_START)){
    	        		methodsCode.append((getMethodSignature(method,getRemoteName()).toString())).append(";").append(lineDelimiter);
    	        }
    	        else{
    	            methodsCode.append((getMethodSignature(method).toString())).append(";").append(lineDelimiter);
    	        }
        }
	    return methodsCode;
	}
	
	public StringBuffer createHomeImplMethods(IType itype,String lineDelimiter,int type)throws JavaModelException{
	    StringBuffer methodsCode = new StringBuffer(); 
	    
	    if(isEntity){
	        
	        methodsCode.append(" protected Class getEntityInterfaceClass(){").append(lineDelimiter);
		    methodsCode.append("    return ").append(getRemoteName()).append(".class;").append(lineDelimiter);
		    methodsCode.append("}").append(lineDelimiter);
		    
	        methodsCode.append("public ").append(getRemoteName()).append(" create() throws javax.ejb.CreateException {").append(lineDelimiter);
	        methodsCode.append("  return (").append(getRemoteName()).append( ") super.createIDO();").append(lineDelimiter);
	        methodsCode.append("}").append(lineDelimiter);
	        
	        methodsCode.append("public ").append(getRemoteName()).append(" findByPrimaryKey(Object pk) throws javax.ejb.FinderException{").append(lineDelimiter);
			methodsCode.append("  return (").append(getRemoteName()).append( ") super.findByPrimaryKeyIDO(pk);").append(lineDelimiter);
			methodsCode.append("}").append(lineDelimiter);
	    }
	
			
		if(type==WizardConstants.IDOLEGACYENTITY){
		    methodsCode.append(" public ").append(getRemoteName()).append(" createLegacy(){").append(lineDelimiter);
		    methodsCode.append("try{").append(lineDelimiter);
			methodsCode.append("return create();").append(lineDelimiter);
			methodsCode.append("}").append(lineDelimiter);
			methodsCode.append("catch(javax.ejb.CreateException ce){").append(lineDelimiter);
			methodsCode.append("throw new RuntimeException(\"CreateException:\"+ce.getMessage());").append(lineDelimiter);
			methodsCode.append("}").append(lineDelimiter);
			methodsCode.append("}").append(lineDelimiter);
			
			
				methodsCode.append("public ").append(getRemoteName()).append(" findByPrimaryKey(int id) throws javax.ejb.FinderException {").append(lineDelimiter);
				methodsCode.append("   return (").append(getRemoteName()).append(") super.findByPrimaryKeyIDO(id);").append(lineDelimiter);
				methodsCode.append("}").append(lineDelimiter);
				
				methodsCode.append(" public ").append(getRemoteName()).append(" findByPrimaryKeyLegacy(int id) throws java.sql.SQLException{").append(lineDelimiter);
			    methodsCode.append("try{").append(lineDelimiter);
				methodsCode.append("return findByPrimaryKey(id);").append(lineDelimiter);
				methodsCode.append("}").append(lineDelimiter);
				methodsCode.append("catch(javax.ejb.FinderException fe){").append(lineDelimiter);
				methodsCode.append("throw new java.sql.SQLException(\"FinderException:\"+fe.getMessage());").append(lineDelimiter);
				methodsCode.append("}").append(lineDelimiter);
				methodsCode.append("}").append(lineDelimiter);
			
			
		}
		else if(isService){
		    
		    methodsCode.append(" protected Class getBeanInterfaceClass(){").append(lineDelimiter);
		    methodsCode.append("    return ").append(getRemoteName()).append(".class;").append(lineDelimiter);
		    methodsCode.append("}").append(lineDelimiter);
		    
		    methodsCode.append("public ").append(getRemoteName()).append(" create() throws javax.ejb.CreateException{").append(lineDelimiter);
			methodsCode.append("  return (").append(getRemoteName()).append( ") super.createIBO();").append(lineDelimiter);
			methodsCode.append("}").append(lineDelimiter);
		}
		
		
	    MethodFilter[] validFilter = {new MethodFilter(WizardConstants.EJB_CREATE_START,MethodFilter.TYPE_PREFIX)
	            ,new MethodFilter(WizardConstants.EJB_HOME_START,MethodFilter.TYPE_PREFIX),
	                    new MethodFilter(WizardConstants.EJB_FIND_START,MethodFilter.TYPE_PREFIX)};
	    List methods = filterMethods(itype.getMethods(),validFilter,null);
	    for (Iterator iter = methods.iterator(); iter.hasNext();) {
            IMethod method = (IMethod) iter.next();
            String realMethodName = method.getElementName();
            if(realMethodName.startsWith(WizardConstants.EJB_FIND_START)){
               if (Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Collection"))) {
                    methodsCode.append((getMethodSignature(method).toString())).append("{").append(lineDelimiter);
                    methodsCode.append("com.idega.data.IDOEntity entity = this.idoCheckOutPooledEntity();").append(lineDelimiter);
                    methodsCode.append("java.util.Collection ids= ((").append(itype.getTypeQualifiedName())
                    		.append(")entity).").append(getMethodCallSignature(method,true)).append(";").append(lineDelimiter);
                    methodsCode.append("this.idoCheckInPooledEntity(entity);").append(lineDelimiter);
                    methodsCode.append("return this.getEntityCollectionForPrimaryKeys(ids);").append(lineDelimiter);
                    methodsCode.append("}").append(lineDelimiter);
    			} else if (Signature.getSimpleName(Signature.toString(method.getReturnType())).equals(Signature.getSimpleName("java.util.Set"))) {
    			    		methodsCode.append((getMethodSignature(method).toString())).append("{").append(lineDelimiter);
                    methodsCode.append("com.idega.data.IDOEntity entity = this.idoCheckOutPooledEntity();").append(lineDelimiter);
                    methodsCode.append("java.util.Set ids= ((").append(itype.getTypeQualifiedName())
                    		.append(")entity).").append(getMethodCallSignature(method,true)).append(";").append(lineDelimiter);
                    methodsCode.append("this.idoCheckInPooledEntity(entity);").append(lineDelimiter);
                    methodsCode.append("return this.getEntitySetForPrimaryKeys(ids);").append(lineDelimiter);
                    methodsCode.append("}").append(lineDelimiter);
    			} else {
    			    methodsCode.append((getMethodSignature(method,getRemoteName()).toString())).append("{").append(lineDelimiter);
                    methodsCode.append("com.idega.data.IDOEntity entity = this.idoCheckOutPooledEntity();").append(lineDelimiter);
                    methodsCode.append("Object pk= ((").append(itype.getTypeQualifiedName())
                    		.append(")entity).").append(getMethodCallSignature(method,true)).append(";").append(lineDelimiter);
                    methodsCode.append("this.idoCheckInPooledEntity(entity);").append(lineDelimiter);
                    methodsCode.append("return this.findByPrimaryKey(pk);").append(lineDelimiter);
                    methodsCode.append("}").append(lineDelimiter);
    			}
            }
            else if(realMethodName.startsWith(WizardConstants.EJB_HOME_START)){
                methodsCode.append((getMethodSignature(method).toString())).append("{").append(lineDelimiter);
                methodsCode.append("com.idega.data.IDOEntity entity = this.idoCheckOutPooledEntity();").append(lineDelimiter);
                methodsCode.append(Signature.toString(method.getReturnType())).append(" theReturn = ((").append(itype.getTypeQualifiedName())
                		.append(")entity).").append(getMethodCallSignature(method,true)).append(";").append(lineDelimiter);
                methodsCode.append("this.idoCheckInPooledEntity(entity);").append(lineDelimiter);
                methodsCode.append("return theReturn;").append(lineDelimiter);
                methodsCode.append("}").append(lineDelimiter);
                
	    	    }
	    	    else if(realMethodName.startsWith(WizardConstants.EJB_CREATE_START)){
	    	        if(isEntity){
		    	        methodsCode.append((getMethodSignature(method,getRemoteName()).toString())).append("{").append(lineDelimiter);
		            methodsCode.append("com.idega.data.IDOEntity entity = this.idoCheckOutPooledEntity();").append(lineDelimiter);
		            methodsCode.append("Object pk = ((").append(itype.getTypeQualifiedName()).append(")entity).")
		            		.append(getMethodCallSignature(method,true)).append(";").append(lineDelimiter);
		            methodsCode.append("((").append(itype.getTypeQualifiedName()).append(")").append("entity ).ejbPostCreate();")
		            		.append(lineDelimiter);
		            methodsCode.append("this.idoCheckInPooledEntity(entity);").append(lineDelimiter);
		            methodsCode.append("try{").append(lineDelimiter).append("return this.findByPrimaryKey(pk);").append(lineDelimiter);
		            methodsCode.append("}").append(lineDelimiter);
		            methodsCode.append("catch(javax.ejb.FinderException fe){throw new com.idega.data.IDOCreateException(fe);}").append(lineDelimiter);
		            methodsCode.append("catch(Exception e){\n\t\tthrow new com.idega.data.IDOCreateException(e);}").append(lineDelimiter);
		            methodsCode.append("}").append(lineDelimiter);
	    	        }
	    	        else if(isService){
	    	            methodsCode.append((getMethodSignature(method,getRemoteName()).toString())).append("{").append(lineDelimiter);
	    	            methodsCode.append("com.idega.business.IBOService service = this.iboCheckOutPooledBean();").append(lineDelimiter);
	    	            methodsCode.append("((").append(itype.getTypeQualifiedName()).append(")entity).")
	            		.append(getMethodCallSignature(method,true)).append(";").append(lineDelimiter);
	    	            methodsCode.append("return ((").append(itype.getTypeQualifiedName()).append(")service");
	    	            methodsCode.append("}").append(lineDelimiter);
	    	            
	    	        }
	    	    } 
	    	    
            
        }
	   
	    return methodsCode;
	}
	
	 private String getPostCreateMethodName(String ejbCreateMethodName){
	      return WizardConstants.EJB_POST_CREATE_START+ejbCreateMethodName.substring(WizardConstants.EJB_CREATE_START.length());
	  }
	
	public List filterMethods(IMethod[] methods,MethodFilter[] validFilterNames,MethodFilter[] nonValidFilterNames)throws JavaModelException{
	    ArrayList list = new ArrayList();
	    for (int i = 0; i < methods.length; i++) {
			IMethod method = methods[i];
			if(Flags.isPublic(method.getFlags()) && !Flags.isStatic(method.getFlags())){
			    String methodName = method.getElementName();
			    boolean valid = false;
			    if(validFilterNames!=null || nonValidFilterNames!=null){
			        
			        if(nonValidFilterNames!=null){
			            valid = true;
					    for (int j = 0; j < nonValidFilterNames.length; j++) {
					        MethodFilter filter = nonValidFilterNames[j];
					        if(filter.filter(methodName))
					            valid &= false;
					        /*
					        if(methodName.indexOf(filter) != -1){
					        		valid &= false;
					        }*/
	                    	}
			        }
			        if(validFilterNames!=null){
					    for (int j = 0; j < validFilterNames.length; j++) {
					        boolean filters = validFilterNames[j].filter(methodName);
					       if(filters)
					           valid |= true;
					       /*
		                    if(methodName.indexOf( validFilterNames[j]) != -1){
		                        valid |= true;
		                    }*/
		                	}
			        }
			        
			    }
			    else{
			       valid = true;
			    }
			    if(valid)
			        list.add(method);
			}
		}
	    return list;
	}
	
	public String cutAwayEJBSuffix(String realMethodName){
	    String methodName = new String(realMethodName);
	    if(realMethodName.startsWith(WizardConstants.EJB_FIND_START)){
	        methodName = "find"+realMethodName.substring(WizardConstants.EJB_FIND_START.length());
	    }
	    else if(realMethodName.startsWith(WizardConstants.EJB_HOME_START)){
	        String firstChar = realMethodName.substring(WizardConstants.EJB_HOME_START.length(),WizardConstants.EJB_HOME_START.length()+1);
	        methodName = firstChar.toLowerCase()+realMethodName.substring(WizardConstants.EJB_HOME_START.length()+1,realMethodName.length());
	    }
	    else if(realMethodName.startsWith(WizardConstants.EJB_CREATE_START)){
	        methodName = "create"+realMethodName.substring(WizardConstants.EJB_CREATE_START.length());
	    } 
	    return methodName;
	}
	
	public StringBuffer getMethodSignature(IMethod method)throws JavaModelException{
	    return getMethodSignature(method,null);
	}
	
	public StringBuffer getMethodSignature(IMethod method,String returnType)throws JavaModelException{
	    StringBuffer methodSignature = new StringBuffer("public ");
	    String realMethodName = method.getElementName();
	    String methodName = realMethodName;
	    if(returnType!=null){
	        methodSignature.append(returnType).append(" ");
	        methodSignature.append(Signature.toString(method.getSignature(),cutAwayEJBSuffix(methodName),method.getParameterNames(),true,false));
	    }
	    else   
	        methodSignature.append(Signature.toString(method.getSignature(),cutAwayEJBSuffix(methodName),method.getParameterNames(),true,true));
	    String[] exceptions = method.getExceptionTypes();
	    boolean hasAddedException = false;
	    boolean hasAddedThrows = false;
	    if(exceptions.length>0){
	        methodSignature.append(" throws ");
	        hasAddedThrows = true;
	        for (int i = 0; i < exceptions.length; i++) {
	            if(i>0 && 1< (exceptions.length+1))
	                methodSignature.append(",");
                methodSignature.append(Signature.toString(exceptions[i]));
                hasAddedException = true;
            }
	    }
	    List exs = getRequiredExceptions();
	    if(exs.size()>0){
	        if(!hasAddedThrows)
	            methodSignature.append(" throws ");
		    for (Iterator iter = exs.iterator(); iter.hasNext();) {
	            String ex = (String) iter.next();
	            if(hasAddedException)
	                methodSignature.append(",");
	            methodSignature.append(ex);
	        	}
	    }
	    
	    return methodSignature;
	}
	
	public void checkImport(IMethod method)throws JavaModelException{
	    String returnType = Signature.toString(method.getReturnType());
	    if(imports.contains(returnType))
	        imports.add(returnType);
	}
	
	public StringBuffer getMethodCallSignature(IMethod method,boolean includeName)throws JavaModelException{
	    StringBuffer signature = new StringBuffer();
	    if(includeName)
	        signature.append(method.getElementName());
	    signature.append("(");
	    String[] parameterNames = method.getParameterNames();
	    for (int i = 0; i < parameterNames.length; i++) {
	        if(i!=0)
	            signature.append(",");
            signature.append(parameterNames[i]);
        }
	    signature.append(")");
	    return signature;
	}
	



	/**
	 * Class used in stub creation routines to add needed imports to a 
	 * compilation unit.
	 */
	public static class ImportsManager implements /* internal */ IImportsStructure {

		private ImportsStructure fImportsStructure;
		private Set fAddedTypes;
		
		/* package */ ImportsManager(IImportsStructure importsStructure) {
			fImportsStructure= (ImportsStructure) importsStructure;
		}
		
		/* package */ ImportsManager(ICompilationUnit createdWorkingCopy) throws CoreException {
			this(createdWorkingCopy, new HashSet());
		}

		/* package */ ImportsManager(ICompilationUnit createdWorkingCopy, Set addedTypes) throws CoreException {
			IPreferenceStore store= PreferenceConstants.getPreferenceStore();
			String[] prefOrder= JavaPreferencesSettings.getImportOrderPreference(store);
			int threshold= JavaPreferencesSettings.getImportNumberThreshold(store);			
			fAddedTypes= addedTypes;
			
			fImportsStructure= new ImportsStructure(createdWorkingCopy, prefOrder, threshold, true);
		}

		/* package */ ICompilationUnit getCompilationUnit() {
			return fImportsStructure.getCompilationUnit();
		}
				
		/**
		 * Adds a new import declaration that is sorted in the existing imports.
		 * If an import already exists or the import would conflict with an import
		 * of an other type with the same simple name, the import is not added.
		 * 
		 * @param qualifiedTypeName The fully qualified name of the type to import
		 * (dot separated).
		 * @return Returns the simple type name that can be used in the code or the
		 * fully qualified type name if an import conflict prevented the import.
		 */				
		public String addImport(String qualifiedTypeName) {
			fAddedTypes.add(qualifiedTypeName);
			return fImportsStructure.addImport(qualifiedTypeName);
		}
		
		/* package */ void create(boolean needsSave, SubProgressMonitor monitor) throws CoreException {
			fImportsStructure.create(needsSave, monitor);
		}
		
		/* package */ void removeImport(String qualifiedName) {
			if (fAddedTypes.contains(qualifiedName)) {
				fImportsStructure.removeImport(qualifiedName);
			}
		}
		
		/* package */ Set getAddedTypes() {
			return fAddedTypes;
		}
		
		String findImport(String simpleName){
		    return fImportsStructure.findImport(simpleName);
		}
	}


    /**
     * @return Returns the homeName.
     */
    public String getHomeName() {
        return homeName;
    }
    /**
     * @param homeName The homeName to set.
     */
    public void setHomeName(String homeName) {
        this.homeName = homeName;
    }
    /**
     * @return Returns the remoteName.
     */
    public String getRemoteName() {
        return remoteName;
    }
    /**
     * @param remoteName The remoteName to set.
     */
    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }
    
    public void addRequiredException(String ExceptionName){
        this.fRequiredExceptions.add(ExceptionName);
    }
    
    public List getRequiredExceptions(){
        	return fRequiredExceptions;
    }
    
    public void resetRequiredExceptions(){
        this.fRequiredExceptions.clear();
    }
    
    public class MethodFilter{
        
        public final static int TYPE_SUFFIX = 0;
        public final static int TYPE_PREFIX = 1;
        public final static int TYPE_WHOLE = 2;
        
        private String filterName;
        private int type;
        
        public MethodFilter(String filter){
            this(filter,TYPE_PREFIX);
        }
        
        public MethodFilter(String filter,int type){
            this.filterName = filter;
            this.type = type;
        }
        
        public String getFilter(){
            return filterName;
        }
        
        public int getType(){
            return type;
        }
        
        /**
         * 
         * @param methodName
         * @return
         */
        public boolean filter(String methodName){
            
            switch (type) {
            case TYPE_PREFIX: 
                return methodName.startsWith(filterName);
                
            case TYPE_WHOLE:
                return methodName.equals(filterName);
               
            case TYPE_SUFFIX:
                return methodName.endsWith(filterName);
               

            }
            return false;
        
        }
    }
}
