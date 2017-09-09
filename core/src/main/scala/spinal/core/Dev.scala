package spinal.core


import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


case class DslContext(clockDomain: ClockDomain, component: Component, scope: ScopeStatement)


trait BaseNode {
    private[core] var algoId = 0
}

//object NameableNode{
//  def unapply(that : NameableNode) : Option[Unit] = Some()
//}

trait NameableExpression extends Expression with Nameable{
  override def foreachExpression(func: (Expression) => Unit): Unit = {}
  override def remapExpressions(func: (Expression) => Expression): Unit = {}


  def rootScopeStatement : ScopeStatement = dslContext.scope
  val statements = mutable.ListBuffer[AssignementStatement]() //TODO IR ! linkedlist  hard

  def hasOnlyOneStatement = statements.length == 1 //TODO faster
  def headStatement = statements.head
  def append(that : AssignementStatement) : this.type = {
    statements += that
    this
  }

  def prepend(that : AssignementStatement) : this.type = {
    statements.prepend(that)
    this
  }

  def foreachStatements(func : (AssignementStatement) => Unit) = {
    statements.foreach(func)
  }

}


trait ExpressionContainer{
  def normalizeInputs: Unit = {}
  def remapExpressions(func : (Expression) => Expression) : Unit
  def remapDrivingExpressions(func : (Expression) => Expression) : Unit = remapExpressions(func)
  def foreachExpression(func : (Expression) => Unit) : Unit
  def foreachDrivingExpression(func : (Expression) => Unit) : Unit = foreachExpression(func)
  def walkExpression(func : (Expression) => Unit) : Unit = {
    foreachExpression(e => {
      func(e)
      e.walkExpression(func)
    })
  }
  def walkDrivingExpressions(func : (Expression) => Unit) : Unit = {
    foreachDrivingExpression(e => {
      func(e)
      e.walkDrivingExpressions(func)
    })
  }
  def walkRemapExpressions(func : (Expression) => Expression) : Unit = {
    remapExpressions(func)
    foreachExpression(e => {
      e.walkRemapExpressions(func)
    })
  }
  def walkRemapDrivingExpressions(func : (Expression) => Expression) : Unit = {
    remapDrivingExpressions(func)
    foreachDrivingExpression(e => {
      e.walkRemapDrivingExpressions(func)
    })
  }
}

trait Expression extends BaseNode with ExpressionContainer{
  def opName : String
}

//object RefExpression{
//  //def apply(s : BaseType) = new RefExpression(s)
//  def unapply(ref : RefExpression) : Option[BaseType] = Some(ref.source)
//}
//
//class RefExpression(val source : BaseType) extends Expression{
//  def opName : String ="(x)"
//  def foreachExpression(func : (Expression) => Unit) : Unit = {
//
//  }
//
//
//  override def remapExpressions(func: (Expression) => Expression): Unit = {}
//
//  def foreachLeafExpression(func : (Expression) => Unit) : Unit = {
//    func(this)
//  }
//}
//
//case class WidthableRefExpression(override val source : BitVector) extends RefExpression(source) with WidthProvider{
//  override def getWidth: Int = source.getWidth
//}

//TODO IR check same scope
object Statement{
  def isFullToFullStatement(s : Statement) = s match {
    case  AssignementStatement(a : NameableExpression,b : NameableExpression) => true
    case _ => false
  }
  def isSomethingToFullStatement(s : Statement) = s match {
    case  AssignementStatement(a : NameableExpression,_) => true
    case _ => false
  }
}
trait Statement extends ExpressionContainer{
  var parentScope : ScopeStatement = null
  var previous, next : Statement = null
  def rootScopeStatement: ScopeStatement = if(parentScope.parentStatement != null) parentScope.parentStatement.rootScopeStatement else parentScope
//  def isConditionalStatement : Boolean

//  def removeStatement() : Unit = parentScope.content -= this
  def removeStatement() : Unit = {
    if(previous != null){
      previous.next = next
    } else {
      parentScope.head = next
    }
    if(next != null){
      next.previous = previous
    } else {
      parentScope.last = previous
    }
    previous = null
    next = null
    parentScope = null
  }

  def foreachStatements(func : (Statement) => Unit)
  def walkStatements(func : (Statement) => Unit): Unit ={
    foreachStatements(s => {
      func(s)
      s.walkStatements(func)
    })
  }

  def walkLeafStatements(func : (LeafStatement) => Unit): Unit ={
    foreachStatements(s => {
      s match {
        case s : LeafStatement => func(s)
        case _ =>  s.walkLeafStatements(func)
      }
    })
  }

  def walkParentTreeStatements(func : (TreeStatement) => Unit) : Unit = {
    if(parentScope.parentStatement != null){
      func(parentScope.parentStatement)
      parentScope.parentStatement.walkParentTreeStatements(func)
    }
  }
}
trait LeafStatement extends Statement{
  final override def foreachStatements(func: (Statement) => Unit): Unit = {}

}
trait TreeStatement extends Statement

//trait AssignementStatementTarget {
//  private [core] def nameableNode : NameableNode
//}


object AssignementStatement{
  def unapply(x : AssignementStatement) : Option[(Expression, Expression)] = Some(x.target, x.source)
}

abstract class AssignementStatement extends LeafStatement{
  var target, source : Expression = null
  override def rootScopeStatement = finalTarget.rootScopeStatement
  def finalTarget = target match{
    case n : NameableExpression => n
    case a : AssignementExpression => a.finalTarget
  }
//  override def isConditionalStatement: Boolean = false
  def foreachExpression(func : (Expression) => Unit) : Unit = {
    func(target)
    func(source)
  }
  override def foreachDrivingExpression(func : (Expression) => Unit) : Unit = {
    target match {
      case ref : NameableExpression =>
      case a : AssignementExpression => a.foreachDrivingExpression(func)
    }
    func(source)
  }


  override def remapDrivingExpressions(func: (Expression) => Expression): Unit = {
    target match {
      case ref : NameableExpression =>
      case a : AssignementExpression => a.remapDrivingExpressions(func)
    }
    source = func(source)
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    target = func(target)
    source = func(source)
  }
}

object DataAssignementStatement{
  def apply(target : Expression, source : Expression) = {
    val ret = new DataAssignementStatement
    ret.target = target
    ret.source = source
    ret.finalTarget.append(ret)
    ret
  }
}

class DataAssignementStatement extends AssignementStatement{

}

object InitAssignementStatement{
  def apply(target : Expression, source : Expression) = {
    val ret = new InitAssignementStatement
    ret.target = target
    ret.source = source
    ret.finalTarget.append(ret)
    ret
  }
}

class InitAssignementStatement extends AssignementStatement{

}

class WhenStatement(var cond : Expression) extends TreeStatement{
  val whenTrue, whenFalse = new ScopeStatement(this)

//  override def isConditionalStatement: Boolean = true

  override def normalizeInputs: Unit = {}

  def foreachStatements(func : (Statement) => Unit) = {
    whenTrue.foreachStatements(func)
    whenFalse.foreachStatements(func)
  }

  def foreachExpression(func : (Expression) => Unit) = {
    func(cond)
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    cond = func(cond)
  }
}

class ScopeStatement(var parentStatement : TreeStatement)/* extends ExpressionContainer*/{
//  val content = mutable.ListBuffer[Statement]() //TODO IR ! linkedlist  hard
//
//  def sizeIsOne = content.length == 1 //TODO faster
//  def head = content.head
//  def append(that : Statement) : this.type = {
//    content += that
//    this
//  }
//
//  def prepend(that : Statement) : this.type = {
//    content.prepend(that)
//    this
//  }
//
//  def foreachStatements(func : (Statement) => Unit) = {
//    content.foreach(func)
//  }
  var head, last : Statement = null

  def sizeIsOne = head != null && head == last
  def prepend(that : Statement) : this.type = {
    if(head != null){
      head.previous = that
    } else {
      last = that
    }
    that.next = head
    that.previous = null

    head = that

    this
  }

  def append(that : Statement) : this.type = {
    that.next = null
    that.previous = last
    if(last != null){
      last.next = that
    } else {
      head = that
    }

    last = that
    this
  }

  def foreachStatements(func : (Statement) => Unit) = {
    var ptr = head
    while(ptr != null){
      func(ptr)
      ptr = ptr.next
    }
  }

  def walkStatements(func : (Statement) => Unit): Unit ={
    foreachStatements(s => {
      func(s)
      s.walkStatements(func)
    })
  }

  def walkLeafStatements(func : (LeafStatement) => Unit): Unit ={
    foreachStatements(s => {
      s match {
        case s : LeafStatement => func(s)
        case _ =>  s.walkLeafStatements(func)
      }
    })
  }

//  def walkExpression(func : (Expression) => Unit): Unit ={
//    walkStatements(s => {
//      s.walkExpression(func)
//    })
//  }
//  override def remapOwnedExpression(func: (Expression) => Expression): Unit = ???
//  override def foreachExpression(func: (Expression) => Unit): Unit = ???
}


//class ScopeStatement(){
//  var head, last : Statement = null
//
//
//  def prepend(that : Statement) : this.type = {
//    if(head != null){
//      head.previous = that
//    }
//    that.next = head
//    that.previous = null
//
//    head = that
//
//    this
//  }
//
//  def append(that : Statement) : this.type = {
//    that.next = null
//    if(last != null){
//      last.next = that
//      that.previous = last
//    }else{
//      that.previous = null
//    }
//
//    last = that
//    this
//  }
//
//  def foreachStatements(func : (Statement) => Unit) = {
//    var ptr = head
//    while(ptr != null){
//      func(ptr)
//      ptr = ptr.next
//    }
//  }
//}


object GraphUtils{
  def walkAllComponents(root : Component, func : Component => Unit) : Unit = {
    func(root)
    root.children.foreach(walkAllComponents(_, func))
  }
}