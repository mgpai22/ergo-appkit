package org.ergoplatform.appkit

import org.ergoplatform.ErgoScriptPredef
import org.ergoplatform.appkit.babelfee.{BabelFeeBox, BabelFeeBoxBuilder, BabelFeeOperations}
import org.ergoplatform.appkit.examples.RunMockedScala.createMockedErgoClient
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.util.Random
import scorex.util.encode.Base16
import sigmastate.interpreter.HintsBag

import java.nio.charset.StandardCharsets
import java.util.Arrays

class BabelFeeSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting
  with AppkitTestingCommon {

  private val script =
    """{
      |
      |    // ===== Contract Information ===== //
      |    // Name: EIP-0031 Babel Fees Contract
      |    // Description: Contract guarding the babel fee box, checking if valid output babel box was recreated and the token exchange was valid.
      |    // Version: 1.0.0
      |
      |    // ===== Relevant Variables ===== //
      |    val babelFeeBoxCreator: SigmaProp = SELF.R4[SigmaProp].get
      |    val ergPricePerToken: Long = SELF.R5[Long].get
      |    val tokenId: Coll[Byte] = _tokenId
      |    val recreatedBabelBoxIndex: Option[Int] = getVar[Int](0)
      |
      |    // ===== Perform Babel Fee Swap ===== //
      |    if (recreatedBabelBoxIndex.isDefined) {
      |
      |        // Check conditions for a valid babel fee swap
      |        val validBabelFeeSwap: Boolean = {
      |
      |            // Output babel fee box
      |            val recreatedBabelBox: Box = OUTPUTS(recreatedBabelBoxIndex.get)
      |
      |            // Check that the babel fee box is recreated correctly
      |            val validBabelFeeBoxRecreation: Boolean =
      |
      |                allOf(Coll(
      |                    (recreatedBabelBox.propositionBytes == SELF.propositionBytes),
      |                    (recreatedBabelBox.tokens(0)._1 == tokenId),
      |                    (recreatedBabelBox.R4[SigmaProp].get == babelFeeBoxCreator),
      |                    (recreatedBabelBox.R5[Long].get == ergPricePerToken),
      |                    (recreatedBabelBox.R6[Coll[Byte]].get == SELF.id)
      |                ))
      |
      |
      |
      |            // Check that the user's token was exchanged correctly
      |            val validBabelFeeExchange: Boolean = {
      |
      |                val nanoErgsDifference: Long = SELF.value - recreatedBabelBox.value
      |                val babelTokensBefore: Long = if (SELF.tokens.size > 0) SELF.tokens(0)._2 else 0L
      |                val babelTokensDifference: Long = recreatedBabelBox.tokens(0)._2 - babelTokensBefore
      |
      |                allOf(Coll(
      |                    (babelTokensDifference * ergPricePerToken >= nanoErgsDifference),
      |                    (nanoErgsDifference >= 0)
      |                ))
      |
      |            }
      |
      |            allOf(Coll(
      |                validBabelFeeBoxRecreation,
      |                validBabelFeeExchange
      |            ))
      |
      |        }
      |
      |        sigmaProp(validBabelFeeSwap)
      |
      |    } else {
      |
      |        // ===== Perform Babel Fee Box Withdrawl ===== //
      |        babelFeeBoxCreator
      |
      |    }
      |
      |}""".stripMargin

  val mockTokenId = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809"

  property("Compile contract for certain token") {
    val ergoClient = new ColdErgoClient(NetworkType.MAINNET, 0)
    val contract = ergoClient.execute { ctx: BlockchainContext =>
      ctx.compileContract(
        ConstantsBuilder.create()
          .item("_tokenId", ErgoId.create(mockTokenId).getBytes)
          .build(),
        script
      )
    }

    println(Base16.encode(contract.getErgoTree.bytes))
  }


  property("babel fee box creation and revoke") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute { ctx: BlockchainContext =>
      val creator = address

      val amountToSend = Parameters.OneErg * 100

      val input1 = ctx.newTxBuilder.outBoxBuilder
        .value(amountToSend + Parameters.MinFee)
        .contract(creator.toErgoContract)
        .build().convertToInputWith(mockTokenId, 0)

      val tx = BabelFeeOperations.createNewBabelContractTx(BoxOperations.createForSender(creator, ctx)
        .withAmountToSpend(amountToSend)
        .withInputBoxesLoader(new MockedBoxesLoader(Arrays.asList(input1))),
        ErgoId.create(mockTokenId),
        Parameters.OneErg);

      ctx.newProverBuilder().build().reduce(tx, 0)

      val babelFeeErgoBox = tx.getOutputs.get(0).convertToInputWith(mockTokenId, 0)

      val babelFeeBox = new BabelFeeBox(babelFeeErgoBox)
    }
  }
}
