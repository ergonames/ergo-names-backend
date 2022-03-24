package scenarios

import scenarios.ProcessMintingRequest
import scenarios.ProcessMintingRequest._
import scenarios.Minter

import org.scalatest.{ PropSpec, Matchers }
import org.ergoplatform.ErgoAddressEncoder
import org.scalatest._
import org.scalatest.Assertions._
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatest.mockito.MockitoSugar

import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.mockito.ArgumentCaptor

import com.amazonaws.services.lambda.runtime.events.{SQSEvent}
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.Context

import play.api.libs.json._


import scala.collection.JavaConverters._

 class TestableMinter extends Minter

class ProcessMintLambdaSpec extends WordSpecLike with Matchers with MockitoSugar {
/*
  "should handle sqs events" in {

    // mocking sqs messages
    val event = mock[SQSEvent]
    val lambdaCtx = mock[Context]
    val request1 = new SQSMessage()
    request1.setBody(Json.toJson(MintRequestSqsMessage("dummy desc1", "box1")).toString())
    val request2 = new SQSMessage()
    request2.setBody(Json.toJson(MintRequestSqsMessage("dummy desc2", "box2")).toString())
    when(event.getRecords).thenReturn(List(request1, request2).asJava)

    // mocking minting function
    val mockedProcessMintingRequest = spy(new TestableMinter)
    doAnswer(_=>"dummyTx")
    .when(mockedProcessMintingRequest)
    .processMintingRequest(any(), any(), any(), any(), any())

    // calling the lambda handler function
    val argumentBoxId = ArgumentCaptor.forClass(classOf[String])
    val argumentDesc = ArgumentCaptor.forClass(classOf[String])
    mockedProcessMintingRequest.lambdaEventHandler(event, lambdaCtx)

    // verifying minting function got called with expected arguments
    verify(mockedProcessMintingRequest, times(2)).processMintingRequest(any(),any(),any(),argumentBoxId.capture(), argumentDesc.capture())
    assert(List("box1", "box2").asJava == argumentBoxId.getAllValues())
    assert(List("dummy desc1", "dummy desc2").asJava == argumentDesc.getAllValues())
  }*/

  // ToDo should handle failing minting requests

  // ToDo should assert that successful mints delete sqs messages

}
