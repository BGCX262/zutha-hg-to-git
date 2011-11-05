package net.zutha.model.topicmap.constructs

import scala.collection.JavaConversions._
import net.zutha.model.topicmap.TMConversions._
import net.zutha.model.constructs._
import org.tmapi.core.{Topic, Association, Role}
import net.zutha.util.Helpers._
import net.zutha.model.db.DB.db
import net.zutha.model.datatypes.PropertyValue

object TMAssociation{
  val getItem = makeCache[Association,String,TMAssociation](_.getId, association => new TMAssociation(association))
  def apply(association: Association):TMAssociation = getItem(association)
}
class TMAssociation protected (association: Association) extends ZAssociation{
  def toZAssociation: ZAssociation = this
  def toAssociation = association
  lazy val reifier = association.getReifier
  def associationType = TMAssociationType(association.getType)
  def associationFields: Set[ZAssociationField] = association.getRoles.toSet.map(TMAssociationField(_:Role))
  def playedRoles = association.getRoleTypes.toSet.map(TMRole(_:Topic))
  def players = association.getRoles.toSet.map((role:Role) => role.getPlayer.toItem)
  def getPlayers(role: ZRole): Set[ZItem] = association.getRoles(role).map(_.getPlayer.toItem).toSet
  def rolePlayers: Set[(ZRole,ZItem)] = playedRoles.flatMap{r => getPlayers(r).map(p => (r,p))}
  def associationProperties: Set[(ZPropertyType,PropertyValue)] = {
    associationType.getAllDefinedProperties.flatMap{pt => getPropertyValues(pt).map(pv => (pt,pv))}
  }

  def getProperties(propType: ZPropertyType) = reifier.getProperties(propType)
  def getPropertyValues(propType: ZPropertyType) = reifier.getPropertyValues(propType)
  def getProperty(propType: ZPropertyType) = reifier.getProperty(propType)
  def getPropertyValue(propType: ZPropertyType) = reifier.getPropertyValue(propType)


  def overriddenBy:Set[ZAssociation] = {
    val ovDeclRoles = association.getReifier.getRolesPlayed(db.siOVERRIDDEN_DECLARATION,db.siOVERRIDES_DECLARATION).toSet
    val overriders = ovDeclRoles.map(_.getParent.getRoles(db.siOVERRIDING_DECLARATION).head.getPlayer
      .getReified.asInstanceOf[Association].toZAssociation)
    overriders
  }
}
