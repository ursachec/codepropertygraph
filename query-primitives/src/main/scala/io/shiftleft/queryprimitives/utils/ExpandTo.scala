package io.shiftleft.queryprimitives.utils

import io.shiftleft.codepropertygraph.generated._
import org.apache.tinkerpop.gremlin.structure.{Direction, Vertex}
import io.shiftleft.queryprimitives.steps._

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object ExpandTo {
  // The call receiver is for java always an object instance.
  // For languages which make use of function pointer this can also be the
  // pointer itself.
  def callReceiverOption(callNode: Vertex): Option[Vertex] = {
    callNode
      .vertices(Direction.OUT, EdgeTypes.RECEIVER)
      .asScala
      .toList
      .headOption
  }

  def callReceiver(callNode: Vertex): Vertex = callReceiverOption(callNode).get

  def callArguments(callNode: Vertex): Iterator[nodes.Expression] =
    callNode.vertices(Direction.OUT, EdgeTypes.AST).asScala.map(_.asInstanceOf[nodes.Expression])

  def argumentToCallOrReturn(argument: Vertex): nodes.Expression = {
    val parent = argument.vertices(Direction.IN, EdgeTypes.AST).nextChecked

    parent match {
      case call: nodes.Call if ExpandTo.isGenericMemberAccessName(call.name) =>
        argumentToCallOrReturn(call)
      case expression: nodes.Expression =>
        expression
    }
  }

  def typeCarrierToType(parameterNode: Vertex): Vertex =
    parameterNode.vertices(Direction.OUT, EdgeTypes.EVAL_TYPE).nextChecked

  def parameterToMethod(parameterNode: Vertex): Vertex =
    parameterNode.vertices(Direction.IN, EdgeTypes.AST).nextChecked

  def formalReturnToMethod(formalReturnNode: Vertex): Vertex =
    formalReturnNode.vertices(Direction.IN, EdgeTypes.AST).nextChecked

  def returnToReturnedExpression(returnExpression: Vertex): Option[nodes.Expression] =
    returnExpression.vertices(Direction.OUT, EdgeTypes.AST).nextOption.map(_.asInstanceOf[nodes.Expression])

  def methodToFormalReturn(method: Vertex): Vertex =
    method
      .vertices(Direction.OUT, EdgeTypes.AST)
      .asScala
      .filter(_.isInstanceOf[nodes.MethodReturn])
      .asJava
      .nextChecked

  def formalReturnToReturn(methodReturn: Vertex): Seq[Vertex] =
    methodReturn
      .vertices(Direction.IN, EdgeTypes.CFG)
      .asScala
      .filter(_.isInstanceOf[nodes.Return])
      .toSeq

  def expressionToMethod(expression: Vertex): Vertex =
    expression.vertices(Direction.IN, EdgeTypes.CONTAINS).nextChecked

  def localToMethod(local: Vertex): Vertex =
    local.vertices(Direction.IN, EdgeTypes.AST).nextChecked

  def hasModifier(methodNode: Vertex, modifierType: String): Boolean =
    methodNode
      .vertices(Direction.OUT, EdgeTypes.AST)
      .asScala
      .exists(astChild =>
        astChild.label == NodeTypes.MODIFIER &&
          astChild.asInstanceOf[nodes.Modifier].modifierType == modifierType)

  def astParent(expression: Vertex): Vertex =
    expression.vertices(Direction.IN, EdgeTypes.AST).nextChecked

  def callToCalledMethod(call: Vertex): Seq[nodes.Method] =
    call
      .vertices(Direction.OUT, EdgeTypes.CALL)
      .asScala
      .map(methodInst => methodInst.vertices(Direction.OUT, EdgeTypes.REF).nextChecked.asInstanceOf[nodes.Method])
      .toSeq

  def methodToTypeDecl(vertex: Vertex): Option[Vertex] =
    findVertex(vertex, _.isInstanceOf[nodes.TypeDecl])

  def methodToFile(vertex: Vertex): Option[Vertex] =
    findVertex(vertex, _.isInstanceOf[nodes.File])

  @tailrec
  private def findVertex(vertex: Vertex, instanceCheck: Vertex => Boolean): Option[Vertex] = {
    val iterator = vertex.vertices(Direction.IN, EdgeTypes.AST)
    if (iterator.hasNext) {
      iterator.next() match {
        case head if instanceCheck(head) => Some(head)
        case head                        => findVertex(head, instanceCheck)
      }
    } else {
      None
    }
  }

  def methodToOutParameters(method: Vertex): Seq[Vertex] =
    method
      .vertices(Direction.OUT, EdgeTypes.AST)
      .asScala
      .filter(_.isInstanceOf[nodes.MethodParameterOut])
      .toSeq

  def allCfgNodesOfMethod(method: Vertex): TraversableOnce[Vertex] =
    method.vertices(Direction.OUT, EdgeTypes.CONTAINS).asScala

  def reference(node: Vertex): Option[Vertex] =
    node.vertices(Direction.OUT, EdgeTypes.REF).nextOption

  /**
    * For a given name, determine whether it is the
    * name of a "member access" operation, e.g.,
    * "<operator>.memberAccess".
    * */
  def isGenericMemberAccessName(name: String): Boolean =
    (name == Operators.memberAccess) ||
      (name == Operators.indirectComputedMemberAccess) ||
      (name == Operators.indirectMemberAccess) ||
      (name == Operators.computedMemberAccess) ||
      (name == Operators.indirection)

}
