package org.tokend.template.test

import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.wallet.*
import org.tokend.wallet.xdr.*
import java.math.BigDecimal

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RedemptionTest {
    @Test
    fun aSerializeDeserializeRequest() {
        val request = RedemptionRequest(
                sourceAccountId = Account.random().accountId,
                assetCode = "OLE",
                amount = BigDecimal("1.5"),
                salt = 123L,
                timeBounds = TimeBounds(0, 999L),
                signature = DecoratedSignature(
                        hint = XdrByteArrayFixed4(byteArrayOf(1, 2, 3, 4)),
                        signature = byteArrayOf(1, 2, 3, 4, 3, 2, 1)
                )
        )

        val netParams = NetworkParams("")

        val serialized = request.serialize(netParams)
        val deserialized = RedemptionRequest.fromSerialized(netParams, serialized)

        Assert.assertEquals(request.sourceAccountId, deserialized.sourceAccountId)
        Assert.assertEquals(request.assetCode, deserialized.assetCode)
        Assert.assertEquals(request.amount, deserialized.amount)
        Assert.assertEquals(request.salt, deserialized.salt)
        Assert.assertEquals(request.timeBounds.minTime, deserialized.timeBounds.minTime)
        Assert.assertEquals(request.timeBounds.maxTime, deserialized.timeBounds.maxTime)
        Assert.assertArrayEquals(request.signature.hint.wrapped, deserialized.signature.hint.wrapped)
        Assert.assertArrayEquals(request.signature.signature, deserialized.signature.signature)
    }

    @Test
    fun bRequestCreationFromTransaction() {
        val netParams = NetworkParams("")
        val account = Account.random()
        val sourceAccountId = account.accountId
        val sourceBalanceId = Base32Check.encodeBalanceId(ByteArray(32))
        val amount = BigDecimal("1.5")
        val destinationAccountId = Base32Check.encodeAccountId(ByteArray(32))
        val assetCode = "OLE"
        val salt = 12345L

        val emptyFee = Fee(0L, 0L, Fee.FeeExt.EmptyVersion())

        val op = PaymentOp(
                sourceBalanceID = PublicKeyFactory.fromBalanceId(sourceBalanceId),
                amount = netParams.amountToPrecised(amount),
                destination = MovementDestination.Account(
                        PublicKeyFactory.fromAccountId(destinationAccountId)
                ),
                feeData = PaymentFeeData(emptyFee, emptyFee, false,
                        PaymentFeeData.PaymentFeeDataExt.EmptyVersion()),
                reference = "$salt",
                subject = "",
                ext = PaymentOp.PaymentOpExt.EmptyVersion(),
                securityType = 0
        )

        val tx = TransactionBuilder(netParams, sourceAccountId)
                .addOperation(Operation.OperationBody.Payment(op))
                .addSigner(account)
                .setSalt(salt)
                .build()

        val timeBounds = tx.timeBounds

        val request = RedemptionRequest.fromTransaction(tx, assetCode)

        Assert.assertEquals(sourceAccountId, request.sourceAccountId)
        Assert.assertEquals(assetCode, request.assetCode)
        Assert.assertEquals(amount, request.amount)
        Assert.assertEquals(salt, request.salt)
        Assert.assertEquals(timeBounds.minTime, request.timeBounds.minTime)
        Assert.assertEquals(timeBounds.maxTime, request.timeBounds.maxTime)
        Assert.assertArrayEquals(tx.signatures[0].hint.wrapped, request.signature.hint.wrapped)
        Assert.assertArrayEquals(tx.signatures[0].signature, request.signature.signature)
    }
}