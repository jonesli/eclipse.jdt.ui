package org.eclipse.jdt.internal.core.refactoring;import org.eclipse.core.runtime.IPath;import org.eclipse.jdt.core.ICompilationUnit;import org.eclipse.jdt.core.JavaModelException;import org.eclipse.jdt.core.refactoring.Refactoring;import org.eclipse.jdt.core.refactoring.RefactoringStatus;import org.eclipse.jdt.internal.compiler.AbstractSyntaxTreeVisitorAdapter;import org.eclipse.jdt.internal.compiler.ast.AstNode;import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;import org.eclipse.jdt.internal.compiler.problem.ProblemHandler;import org.eclipse.jdt.internal.core.CompilationUnit;
public abstract class AbstractRefactoringASTAnalyzer  extends AbstractSyntaxTreeVisitorAdapter{

	private RefactoringStatus fResult;
	private CompilationUnit fCu;
	
	private int[] fLineSeparatorPositions; //set in visit(CompilationUnitDeclaration)
	
	public final RefactoringStatus analyze(ICompilationUnit cu) throws JavaModelException{
		fResult= new RefactoringStatus();
		fCu= (CompilationUnit)cu;
		fCu.accept(this);
		return fResult;	
	}
	
	public boolean doVisit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope){
		return true;
	}
	
	/* non java-doc
	 * sublasses implement doVisit instead
	 */ 
	public final boolean visit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope) {
		fLineSeparatorPositions= compilationUnitDeclaration.compilationResult.lineSeparatorPositions;
		return doVisit(compilationUnitDeclaration, scope);
	}
	
	protected int getLineNumber(AstNode node){
		Assert.isNotNull(fLineSeparatorPositions);
		return ProblemHandler.searchLineNumber(fLineSeparatorPositions, node.sourceStart);
	}
	
	protected void addError(String msg){
		fResult.addError(msg);
	}
	
	protected void addWarning(String msg){
		fResult.addWarning(msg);
	}
	
	protected static String getFullPath(ICompilationUnit cu) {
		Assert.isTrue(cu.exists());
		IPath path= null;
		try {
			return Refactoring.getResource(cu).getFullPath().toString();
		} catch (JavaModelException e) {
			return cu.getElementName();
		}
	}

	protected String cuFullPath() {
		return getFullPath(fCu);
	}	
}
