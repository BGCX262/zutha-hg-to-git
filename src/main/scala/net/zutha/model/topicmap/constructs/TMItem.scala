package net.zutha.model.topicmap.constructs

import scala.collection.JavaConversions._
import net.zutha.model.topicmap.TMConversions._
import net.zutha.util.Cache._
import net.zutha.model.constants.ZuthaConstants
import ZuthaConstants._
import net.zutha.model.constructs._
import net.zutha.model.db.DB.db
import de.topicmapslab.majortom.model.core.IScope
import org.tmapi.core.{Name, Topic}
import net.zutha.model.datatypes.{PropertyValue}

object TMItem{
  val getItem = makeCache[Topic,String,TMItem](_.getId, topic => new TMItem(topic))
  def apply(topic: Topic):TMItem = getItem(topic)
}
class TMItem protected (topic: Topic) extends ZItem{
  val tm = topic.getTopicMap

  // -------------- Common method overrides --------------
  override def hashCode() = ("http://zutha.net/majortomTopic/" + topic.getId).hashCode()

  override def equals(obj:Any) = obj match {
    case item: ZItem => item.hashCode == hashCode
    case topic: Topic => topic.toItem.hashCode == hashCode
    case _ => false
  }

  // -------------- Conversion --------------
  def toTopic = topic
  def toItem: ZItem = this

  lazy val isType = db.itemIsA(this,db.TYPE)
  def toType: ZType = TMType(topic)

  def isItemType = db.itemIsA(this,db.ITEM_TYPE)
  def toItemType: ZItemType = TMItemType(topic)

  lazy val isTrait = db.itemIsA(this,db.TRAIT)
  def toTrait: ZTrait = TMTrait(topic)

  lazy val isRole = db.itemIsA(this,db.ROLE)
  def toRole: ZRole = TMRole(topic)

  lazy val isAssociationType = db.itemIsA(this,db.ASSOCIATION_TYPE)
  def toAssociationType: ZAssociationType = TMAssociationType(topic)

  lazy val isPropertyType = db.itemIsA(this,db.PROPERTY_TYPE)
  def toPropertyType: ZPropertyType = TMPropertyType(topic)

  lazy val isAssocPropertyType = db.itemIsA(this,db.ASSOCIATION_PROPERTY_TYPE)
  def toAssocPropertyType: ZAssociationPropertyType = TMAssociationPropertyType(topic)

  // -------------- ZIDs --------------
  lazy val ZIDs: Set[String] = {
    val zids = topic.getSubjectIdentifiers.map(_.toExternalForm)
      .filter{_.startsWith(ZID_PREFIX.toString)}.map{_.replace(ZID_PREFIX.toString,"")}
      .toSet
    //every item must have at least one ZID
    if (zids.size == 0){
      throw new Exception("item has no ZIDs")
    }

    try{
      zids.map{Zid(_).toString}
    } catch {
      case e: IllegalArgumentException => throw new Exception("item has an invalid ZID")
    }
  }
  def getZIDs = ZIDs

  def zid: String = getZIDs.toSeq.sorted.head

  def addZID(zid: Zid){
    val zidLoc = topic.getTopicMap.createLocator(ZID_PREFIX + zid)
    topic.addSubjectIdentifier(zidLoc)
  }

  // -------------- names --------------
  def names(scope: ZScope):Set[String] = topic.getNames(scope:IScope).toSet.map((_:Name).getValue)
  def names(scopeItems: ZItem*):Set[String] = names(ZScope(scopeItems.toSet))
  def allNames:Set[String] = topic.getNames.toSet.map((_:Name).getValue)
  def unconstrainedNames:Set[String] = names(ZScope())

  def name(scope: ZScope) = names(scope).headOption
  def name(scopeItems: ZItem*) = names(ZScope(scopeItems.toSet)).headOption
  def name = unconstrainedNames.headOption match {
    case Some(str) => str
    case None => { //TODO implement autoNames
      "Item " + zid
      //throw new SchemaViolationException("item '" + this + "' has no name")
    }
  }
  
  // -------------- types --------------
  def hasType(zType: ZType): Boolean = getAllTypes.contains(zType)

  def itemType = db.directTypesOfItem(this).collect{
    case ZItemType(it) => it
  }.head

  def getAllTypes = db.allTypesOfItem(this).toSet

  def getFieldDefiningTypes = {
    val fieldDefiningTypes = getAllTypes.filter(_.declaresFields)
    fieldDefiningTypes
  }

  // -------------- fields --------------

  def getPropertySetsGrouped = {
    val kvPairs: Set[(ZType,Set[ZPropertySet])] = getFieldDefiningTypes.map{definingType =>
      val propSets: Set[ZPropertySet] = definingType.declaredPropertySets
        //.filterNot(_.isAbstract) //abstract propTypes do not have associated propSets
        .map(propSetType => ZPropertySet(this,propSetType))
      (definingType,propSets)
    }
    kvPairs.toMap
  }

  def getNonEmptyPropertySetsGrouped = getPropertySetsGrouped.flatMap{
    case (definingType,propSets) => propSets.filterNot(_.isEmpty) match{
      case propSet if propSet.isEmpty => None
      case propSet => Some(definingType,propSet)
    }
  }

  def getPropertySets = getPropertySetsGrouped.flatMap(_._2).toSet

  def getProperties(propType: ZPropertyType): Set[ZProperty] = {
    //check if propType is a name
    if(propType.hasAncestor(db.NAME)){ //no need to check if propType is zsi:name itself because zsi:name is abstract
      val names = topic.getNames.map(_.toProperty).toSet
      return names
    }

    //TODO check if propType is ZID


    //propType is an occurrence-implemented property
    val occurrences = topic.getOccurrences(propType).map(_.toProperty).toSet
    occurrences
  }

  def getPropertyValues(propType: ZPropertyType): Set[PropertyValue] = {
    getProperties(propType).map(prop => prop.value)
  }

  def getProperty(propType: ZPropertyType) = getProperties(propType).headOption

  def getPropertyValue(propType: ZPropertyType): Option[PropertyValue] =
    getPropertyValues(propType).headOption


  lazy val getAssociationFieldSetsGrouped = {
    val kvPairs: Set[(ZType,Set[ZAssociationFieldSet])] = getFieldDefiningTypes.map{definingType =>
      val assocFieldSets: Set[ZAssociationFieldSet] = definingType.declaredAssociationFieldSets
        .map{ZAssociationFieldSet(this,_)}
      (definingType,assocFieldSets)
    }
    kvPairs.toMap
  }

  def getNonEmptyAssociationFieldSetsGrouped = getAssociationFieldSetsGrouped.flatMap{
    case (definingType,assocFieldSets) => assocFieldSets.filterNot(_.isEmpty) match{
      case assocFieldSet if assocFieldSet.isEmpty => None
      case assocFieldSet => Some(definingType,assocFieldSet)
    }
  }

  lazy val getAssociationFieldSets = getAssociationFieldSetsGrouped.flatMap(_._2).toSet

  def getAssociationFieldSet(role: ZRole, assocType: ZAssociationType) = {
      getAssociationFieldSets.filter(fieldSet => fieldSet.role == role && fieldSet.associationType == assocType).headOption
  }

  def getAssociationFields(assocFieldType: ZAssociationFieldType) = {
    getAssociationFields(assocFieldType.role, assocFieldType.associationType)
  }
  def getAssociationFields(role: ZRole, assocType: ZAssociationType) = {
    val rolesPlayed = topic.getRolesPlayed(role, assocType).toSet
    val visibleRolesPlayed = rolesPlayed.filterNot(_.getParent.isAnonymous)
    visibleRolesPlayed.map{r => TMAssociationField(r)}
  }
}
