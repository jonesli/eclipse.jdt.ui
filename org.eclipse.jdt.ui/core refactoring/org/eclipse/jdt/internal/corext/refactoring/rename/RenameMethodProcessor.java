/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ISearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResult;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.changes.ValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

public abstract class RenameMethodProcessor extends JavaRenameProcessor implements IReferenceUpdating {
	
	private SearchResultGroup[] fOccurrences;
	private boolean fUpdateReferences;
	private IMethod fMethod;
	private TextChangeManager fChangeManager;
	private ICompilationUnit[] fNewWorkingCopies;
	private WorkingCopyOwner fWorkingCopyOwner;
	
	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renameMethodProcessor"; //$NON-NLS-1$
	
	public RenameMethodProcessor(IMethod method) {
		initialize(method);
	}
	
	protected void initialize(IMethod method) {
		fMethod= method;
		setNewElementName(fMethod.getElementName());
		fUpdateReferences= true;
		fWorkingCopyOwner= new WorkingCopyOwner() { };
	}	
	
	protected void setData(RenameMethodProcessor other) {
		fUpdateReferences= other.fUpdateReferences;
		setNewElementName(other.getNewElementName());
	}
	
	//---- IRefactoringProcessor --------------------------------

	public String getIdentifier() {
		return IDENTIFIER;
	}
	
	public boolean isApplicable() throws CoreException {
		if (fMethod == null)
			return false;
		if (! Checks.isAvailable(fMethod))
			return false;
		if (fMethod.isConstructor())
			return false;
		if (isSpecialCase(fMethod))
			return false;
		return true;
	}
	
	public String getProcessorName() {
		return RefactoringCoreMessages.getFormattedString(
			"RenameMethodRefactoring.name", //$NON-NLS-1$
			new String[]{fMethod.getElementName(), getNewElementName()});
	}
	
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fMethod);
	}

	public Object[] getElements() {
		return new Object[] {fMethod};
	}

	protected void loadDerivedParticipants(List result, String[] natures, SharableParticipants shared) throws CoreException {
		// TODO must caclulate ripple ??
	}
	
	//---- IRenameProcessor -------------------------------------
	
	public final String getCurrentElementName(){
		return fMethod.getElementName();
	}
		
	public final RefactoringStatus checkNewElementName(String newName) {
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkMethodName(newName);
		if (Checks.isAlreadyNamed(fMethod, newName))
			result.addFatalError(RefactoringCoreMessages.getString("RenameMethodRefactoring.same_name")); //$NON-NLS-1$
		return result;
	}
	
	public Object getNewElement() {
		return fMethod.getDeclaringType().getMethod(getNewElementName(), fMethod.getParameterTypes());
	}
	
	public final IMethod getMethod() {
		return fMethod;
	}
	
	//---- IReferenceUpdating -----------------------------------

	public boolean canEnableUpdateReferences() {
		return true;
	}

	public final void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}	
	
	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}	
	
	//----------- preconditions ------------------

	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		if (fMethod == null || ! fMethod.exists()){
			String message= RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.deleted", //$NON-NLS-1$
								fMethod.getCompilationUnit().getElementName());
			return RefactoringStatus.createFatalErrorStatus(message);
		}	
		
		RefactoringStatus result= Checks.checkAvailability(fMethod);
		if (result.hasFatalError())
				return result;
		result.merge(Checks.checkIfCuBroken(fMethod));
		if (JdtFlags.isNative(fMethod))
			result.addError(RefactoringCoreMessages.getString("RenameMethodRefactoring.no_native")); //$NON-NLS-1$
		return result;
	}

	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		try{
			RefactoringStatus result= new RefactoringStatus();
			pm.beginTask("", 4); //$NON-NLS-1$
			// TODO workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=40367
			if (!Checks.isAvailable(fMethod)) {
				result.addFatalError("Method to be renamed is binary.", JavaStatusContext.create(fMethod));
				return result;
			}
			result.merge(Checks.checkIfCuBroken(fMethod));
			if (result.hasFatalError())
				return result;
			pm.setTaskName(RefactoringCoreMessages.getString("RenameMethodRefactoring.taskName.checkingPreconditions")); //$NON-NLS-1$
			result.merge(checkNewElementName(getNewElementName()));
			pm.worked(1);
			
			pm.setTaskName(RefactoringCoreMessages.getString("RenameMethodRefactoring.taskName.searchingForReferences")); //$NON-NLS-1$
			fOccurrences= getOccurrences(new SubProgressMonitor(pm, 4));	
			pm.setTaskName(RefactoringCoreMessages.getString("RenameMethodRefactoring.taskName.checkingPreconditions")); //$NON-NLS-1$
			
			if (fUpdateReferences)
				result.merge(checkRelatedMethods(new SubProgressMonitor(pm, 1)));
			else
				pm.worked(1);
			
			if (fUpdateReferences)
				result.merge(analyzeCompilationUnits());	
			pm.worked(1);
			
			if (result.hasFatalError())
				return result;
			
			if (fUpdateReferences)
				result.merge(analyzeRenameChanges(new SubProgressMonitor(pm, 1)));
				// TODO: analyzeRenameChanges already calls createChangeManager(pm), but throws it away!
				
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 3));
			result.merge(validateModifiesFiles());
			return result;
		} finally{
			pm.done();
		}	
	}
	
	protected final IJavaSearchScope createRefactoringScope() throws CoreException {
		return createRefactoringScope(fMethod);
	}
	
	protected static final IJavaSearchScope createRefactoringScope(IMethod method) throws CoreException {
		return RefactoringScopeFactory.create(method);
	}
	
	ISearchPattern createOccurrenceSearchPattern(IProgressMonitor pm) throws CoreException {
		return createSearchPatternWithOwner(pm, fMethod);
	}

	private ISearchPattern createSearchPatternWithOwner(IProgressMonitor pm, IMethod method) throws CoreException {
		HashSet methods= new HashSet();
		methods.add(method);
		IMethod[] rippleMethods= RippleMethodFinder2.getRelatedMethods(method, pm, fWorkingCopyOwner);
		methods.addAll(Arrays.asList(rippleMethods));
		IMethod[] ms= (IMethod[]) methods.toArray(new IMethod[methods.size()]);
		return RefactoringSearchEngine.createSearchPattern(ms, IJavaSearchConstants.ALL_OCCURRENCES);
	}
	
	SearchResultGroup[] getOccurrences(){
		return fOccurrences;	
	}
	
	/*
	 * XXX made protected to allow overriding and working around bug 39700
	 */
	protected SearchResultGroup[] getOccurrences(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 2);	 //$NON-NLS-1$
		ISearchPattern pattern= createOccurrenceSearchPattern(new SubProgressMonitor(pm, 1));
		return RefactoringSearchEngine.search(createRefactoringScope(), pattern,
			new MethodOccurenceCollector(new SubProgressMonitor(pm, 1), getMethod().getElementName()));	
	}

	private static boolean isSpecialCase(IMethod method) throws CoreException {
		if (  method.getElementName().equals("toString") //$NON-NLS-1$
			&& (method.getNumberOfParameters() == 0)
			&& (method.getReturnType().equals("Ljava.lang.String;")  //$NON-NLS-1$
				|| method.getReturnType().equals("QString;") //$NON-NLS-1$
				|| method.getReturnType().equals("Qjava.lang.String;"))) //$NON-NLS-1$
			return true;		
		else return (method.isMainMethod());
	}
	
	private static RefactoringStatus checkIfConstructorName(IMethod method, String newName) {
		return Checks.checkIfConstructorName(method, newName, method.getDeclaringType().getElementName());
	}
	
	private RefactoringStatus checkRelatedMethods(IProgressMonitor pm) throws CoreException { 
		RefactoringStatus result= new RefactoringStatus();
		//TODO: RippleMethodFinder2? can WorkingCopyOwner be null there?
		Set methodsToRename= new HashSet(Arrays.asList(RippleMethodFinder.getRelatedMethods(fMethod, pm, null)));
		for (Iterator iter= methodsToRename.iterator(); iter.hasNext(); ) {
			IMethod method= (IMethod)iter.next();
			
			result.merge(checkIfConstructorName(method, getNewElementName()));
			
			String[] msgData= new String[]{method.getElementName(), JavaModelUtil.getFullyQualifiedName(method.getDeclaringType())};
			if (! method.exists()){
				result.addFatalError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.not_in_model", msgData)); //$NON-NLS-1$ 
				continue;
			}
			if (method.isBinary())
				result.addFatalError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.no_binary", msgData)); //$NON-NLS-1$
			if (method.isReadOnly())
				result.addFatalError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.no_read_only", msgData));//$NON-NLS-1$
			if (JdtFlags.isNative(method))
				result.addError(RefactoringCoreMessages.getFormattedString("RenameMethodRefactoring.no_native_1", msgData));//$NON-NLS-1$
		}
		return result;	
	}
	
	private IFile[] getAllFilesToModify() {
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}
	
	private RefactoringStatus validateModifiesFiles() {
		return Checks.validateModifiesFiles(getAllFilesToModify());
	}
	
	private RefactoringStatus analyzeCompilationUnits() throws CoreException {
		if (fOccurrences.length == 0)
			return null;
			
		RefactoringStatus result= new RefactoringStatus();
		fOccurrences= Checks.excludeCompilationUnits(fOccurrences, result);
		if (result.hasFatalError())
			return result;
		
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fOccurrences));	
			
		return result;
	}
	
	//-------
	private RefactoringStatus analyzeRenameChanges(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 3); //$NON-NLS-1$
			
			TextChangeManager manager= createChangeManager(new SubProgressMonitor(pm, 1));
			SearchResultGroup[] oldOccurrences= getOccurrences();
			SearchResultGroup[] newOccurrences= getNewOccurrences(new SubProgressMonitor(pm, 1), manager);
			RefactoringStatus result= RenameAnalyzeUtil.analyzeRenameChanges(manager, oldOccurrences, newOccurrences);
			return result;
		} finally{
			pm.done();
			if (fNewWorkingCopies != null){
				for (int i= 0; i < fNewWorkingCopies.length; i++) {
					fNewWorkingCopies[i].discardWorkingCopy();		
				}
			}	
		}
	}

	private SearchResultGroup[] getNewOccurrences(IProgressMonitor pm, TextChangeManager manager) throws CoreException {
		pm.beginTask("", 3); //$NON-NLS-1$
		try {
			ICompilationUnit[] compilationUnitsToModify= manager.getAllCompilationUnits();
			fNewWorkingCopies= RenameAnalyzeUtil.createNewWorkingCopies(
					compilationUnitsToModify, manager, fWorkingCopyOwner, new SubProgressMonitor(pm, 1));
			
			ICompilationUnit declaringCuWorkingCopy= RenameAnalyzeUtil.findWorkingCopyForCu(fNewWorkingCopies, fMethod.getCompilationUnit());
			if (declaringCuWorkingCopy == null)
				return new SearchResultGroup[0];
			
			IMethod method= getNewMethod(declaringCuWorkingCopy);
			if (method == null || ! method.exists())
				return new SearchResultGroup[0];
			
			ISearchPattern newPattern= createSearchPatternWithOwner(new SubProgressMonitor(pm, 1), method);
			return RefactoringSearchEngine.search(fWorkingCopyOwner, createRefactoringScope(method), newPattern,
					new MethodOccurenceCollector(new SubProgressMonitor(pm, 1), method.getElementName()));
		} finally{
			pm.done();
		}	
	}
	
	private IMethod getNewMethod(ICompilationUnit newWorkingCopyOfDeclaringCu) throws CoreException{
		IType[] allNewTypes= newWorkingCopyOfDeclaringCu.getAllTypes();
		String fullyTypeName= fMethod.getDeclaringType().getFullyQualifiedName();
		String[] paramTypeSignatures= fMethod.getParameterTypes();
		for (int i= 0; i < allNewTypes.length; i++) {
			if (allNewTypes[i].getFullyQualifiedName().equals(fullyTypeName))
				return allNewTypes[i].getMethod(getNewElementName(), paramTypeSignatures);
		}
		return null;
	}

	//-------
	private IMethod[] classesDeclareMethodName(ITypeHierarchy hier, List classes, IMethod method, String newName)  throws CoreException {
		Set result= new HashSet();
		IType type= method.getDeclaringType();
		List subtypes= Arrays.asList(hier.getAllSubtypes(type));
		
		int parameterCount= method.getParameterTypes().length;
		boolean isMethodPrivate= JdtFlags.isPrivate(method);
		
		for (Iterator iter= classes.iterator(); iter.hasNext(); ){
			IType clazz= (IType) iter.next();
			IMethod[] methods= clazz.getMethods();
			boolean isSubclass= subtypes.contains(clazz);
			for (int j= 0; j < methods.length; j++) {
				IMethod foundMethod= Checks.findMethod(newName, parameterCount, false, new IMethod[] {methods[j]});
				if (foundMethod == null)
					continue;
				if (isSubclass || type.equals(clazz))
					result.add(foundMethod);
				else if ((! isMethodPrivate) && (! JdtFlags.isPrivate(methods[j])))
					result.add(foundMethod);
			}
		}
		return (IMethod[]) result.toArray(new IMethod[result.size()]);
	}

	final IMethod[] hierarchyDeclaresMethodName(IProgressMonitor pm, IMethod method, String newName) throws CoreException {
		Set result= new HashSet();
		IType type= method.getDeclaringType();
		ITypeHierarchy hier= type.newTypeHierarchy(pm);
		IMethod foundMethod= Checks.findMethod(newName, method.getParameterTypes().length, false, type);
		if (foundMethod != null) 
			result.add(foundMethod);

		IMethod[] foundInHierarchyClasses= classesDeclareMethodName(hier, Arrays.asList(hier.getAllClasses()), method, newName);
		if (foundInHierarchyClasses != null)
			result.addAll(Arrays.asList(foundInHierarchyClasses));
		
		IType[] implementingClasses= hier.getImplementingClasses(type);	
		IMethod[] foundInImplementingClasses= classesDeclareMethodName(hier, Arrays.asList(implementingClasses), method, newName);
		if (foundInImplementingClasses != null)
			result.addAll(Arrays.asList(foundInImplementingClasses));
		return (IMethod[]) result.toArray(new IMethod[result.size()]);	
	}
				
	//-------- changes -----
	
	public final Change createChange(IProgressMonitor pm) throws CoreException {
		try{
			return new ValidationStateChange(RefactoringCoreMessages.getString("Change.javaChanges"), fChangeManager.getAllChanges()); //$NON-NLS-1$
		} finally{
			pm.done();
		}	
	}
	
	private TextChangeManager createChangeManager(IProgressMonitor pm) throws CoreException {
		TextChangeManager manager= new TextChangeManager(true);
		
		/* don't really want to add declaration and references separetely in this refactoring 
		* (declarations of methods are different than declarations of anything else)
		*/
		if (! fUpdateReferences)
			addDeclarationUpdate(manager); // TODO: only one declaration updated, not all of them
		else
			addOccurrences(manager, pm);	
		return manager;
	}
	
	void addOccurrences(TextChangeManager manager, IProgressMonitor pm) throws CoreException {
		pm.beginTask("", fOccurrences.length);				 //$NON-NLS-1$
		for (int i= 0; i < fOccurrences.length; i++){
			ICompilationUnit cu= fOccurrences[i].getCompilationUnit();
			if (cu == null)	
				continue;
			
			SearchResult[] results= fOccurrences[i].getSearchResults();
			for (int j= 0; j < results.length; j++){
				String editName= RefactoringCoreMessages.getString("RenameMethodRefactoring.update_occurrence"); //$NON-NLS-1$
				TextChangeCompatibility.addTextEdit(manager.get(cu), editName, createTextChange(results[j]));
			}
			pm.worked(1);
		}		
	}
	
	private void addDeclarationUpdate(TextChangeManager manager) throws CoreException {
		ICompilationUnit cu= fMethod.getCompilationUnit();
		TextChange change= manager.get(cu);
		addDeclarationUpdate(change);
	}
	
	final void addDeclarationUpdate(TextChange change) throws CoreException {
		TextChangeCompatibility.addTextEdit(change, RefactoringCoreMessages.getString("RenameMethodRefactoring.update_declaration"), new ReplaceEdit(fMethod.getNameRange().getOffset(), fMethod.getNameRange().getLength(), getNewElementName()));
	}
	
	final TextEdit createTextChange(SearchResult searchResult) {
		return new ReplaceEdit(searchResult.getStart(), searchResult.getEnd() - searchResult.getStart(), getNewElementName());
	}
}	
