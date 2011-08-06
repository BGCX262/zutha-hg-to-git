package net.zutha.model.topicmap

import scala.collection.JavaConversions._
import net.zutha.model.{ProposedItem, Item, ZID}
import net.zutha.model.db.{TMQL, MajortomDB, DB}
import org.tmapi.core._
import de.topicmapslab.majortom.model.core.{ITopicMap, ITopicMapSystem}
import de.topicmapslab.majortom.model.transaction.ITransaction

class TopicMapDB extends DB with MajortomDB with TMQL with TMConstructExtensions{
  val ZuthaTopicMapURI = "http://zutha.net"

  val ENABLE_TRANSACTIONS = false

  
  lazy val sys: ITopicMapSystem = makeTopicSystem
//  lazy val store: ITopicMapStore = makeTopicMapStore(sys)
  lazy val tm: ITopicMap = sys.createTopicMap(ZuthaTopicMapURI).asInstanceOf[ITopicMap]

  def getItem(zid: ZID) = tm.lookupItemByZID(zid)

  def createItem(item: ProposedItem) {
    //start transaction
    val txn = if(ENABLE_TRANSACTIONS) tm.createTransaction() else tm

    val topic = txn.createTopic()

    //add names
    for(nameProp <- item.names.props){
      val name = topic.createName(nameProp.value)
      txn.lookupTopicByZSI(nameProp.typeZSI) match {
        case Some(tt) => name.setType(tt)
        case _ => //TODO: invalid name type
      }
    }

    //add Subject Indicators
    for(siProp <- item.subjectIndicators.props){
      try{
        val loc = txn.createLocator(siProp.uri)
        topic.addSubjectIdentifier(loc)
      } catch{
        case e:MalformedIRIException => //send error message to siProp
      }
    }

    //add Topic Types
    for(typeProp <- item.types.props){
      txn.lookupTopicByZSI(typeProp.typeZSI) match {
        case Some(tt) => topic.addType(tt)
        case _ => //do nothing
      }
    }

    //commit proposed item
    if(ENABLE_TRANSACTIONS) txn.asInstanceOf[ITransaction].commit
  }



  def printTMLocators = {
    for(val loc <- sys.getLocators) {
      println (loc.getReference)
    }
  }


}