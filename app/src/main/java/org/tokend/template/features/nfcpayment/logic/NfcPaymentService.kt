package org.tokend.template.features.nfcpayment.logic

import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.CompletableSubject
import io.reactivex.subjects.PublishSubject
import org.tokend.sdk.utils.extentions.encodeHexString
import org.tokend.template.features.nfcpayment.model.ClientToPosResponse
import org.tokend.template.features.nfcpayment.model.PosToClientCommand
import org.tokend.template.features.nfcpayment.model.RawPosPaymentRequest
import org.tokend.template.util.Navigator
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.TransactionEnvelope
import org.tokend.wallet.xdr.utils.XdrDataOutputStream
import java.io.ByteArrayOutputStream

@RequiresApi(Build.VERSION_CODES.KITKAT)
class NfcPaymentService : HostApduService() {
    private lateinit var compositeDisposable: CompositeDisposable
    private var lastSentTransactionReference: String = ""

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        Log.i(LOG_TAG, "Received ${commandApdu.encodeHexString()}")

        val command = try {
            PosToClientCommand.fromBytes(commandApdu)
        } catch (e: Exception) {
            e.printStackTrace()
            return ClientToPosResponse.Empty.data
        }

        val response: ClientToPosResponse? = when (command) {
            is PosToClientCommand.SelectAid -> {
                isActive = true
                ClientToPosResponse.Ok
            }
            is PosToClientCommand.SendPaymentRequest -> {
                val readyEnvelope = getReadyTransactionEnvelopeForRequest(command.request)

                if (readyEnvelope == null) {
                    onPaymentRequestReceived(command.request)
                    null
                } else {
                    lastSentTransactionReference = command.request.referenceString
                    ClientToPosResponse.PaymentTransaction(readyEnvelope)
                }
            }
            is PosToClientCommand.Ok -> {
                onOk()
                ClientToPosResponse.Empty
            }
            is PosToClientCommand.Error -> {
                onError()
                ClientToPosResponse.Empty
            }
        }

        return response?.data.also { Log.i(LOG_TAG, "Send ${it?.encodeHexString()}") }
    }

    private fun getReadyTransactionEnvelopeForRequest(request: RawPosPaymentRequest)
            : ByteArray? {
        return readyTransactionsByReference[request.referenceString]?.toXdrBytes()
                ?: return null
    }

    private fun onPaymentRequestReceived(request: RawPosPaymentRequest) {
        Navigator.from(applicationContext).openNfcPayment(request)
    }

    private fun onOk() {
        resultSubjectsByReference[lastSentTransactionReference]?.onComplete()
        forgetReference(lastSentTransactionReference)
    }

    private fun onError() {
        resultSubjectsByReference[lastSentTransactionReference]?.onError(Exception())
        forgetReference(lastSentTransactionReference)
    }

    private fun onNewReadyTransaction(envelope: TransactionEnvelope) {
        if (isActive) {
            lastSentTransactionReference = getReferenceFromEnvelope(envelope)
            sendResponseApdu(ClientToPosResponse.PaymentTransaction(envelope.toXdrBytes()).data)
        }
    }

    private fun forgetReference(reference: String) {
        resultSubjectsByReference.remove(reference)
        readyTransactionsByReference.remove(reference)
    }

    override fun onDeactivated(reason: Int) {
        Log.i(LOG_TAG, "Deactivated with reason $reason")
        isActive = false
    }

    override fun onCreate() {
        super.onCreate()
        compositeDisposable = CompositeDisposable()
        newTransactionsSubject
                .subscribe(this::onNewReadyTransaction)
                .addTo(compositeDisposable)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }

    private fun TransactionEnvelope.toXdrBytes(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        toXdr(XdrDataOutputStream(outputStream))
        return outputStream.toByteArray()
    }

    companion object : TransactionBroadcaster {
        private const val LOG_TAG = "NfcPayments"

        private val readyTransactionsByReference = mutableMapOf<String, TransactionEnvelope>()
        private val newTransactionsSubject = PublishSubject.create<TransactionEnvelope>()
        private val resultSubjectsByReference = mutableMapOf<String, CompletableSubject>()

        var isActive: Boolean = false
            private set

        override fun broadcastTransaction(transaction: Transaction): Completable {
            val envelope = transaction.getEnvelope()
            val reference = getReferenceFromEnvelope(envelope)

            readyTransactionsByReference[reference] = envelope
            newTransactionsSubject.onNext(envelope)

            val resultSubject = CompletableSubject.create()
            resultSubjectsByReference[reference] = resultSubject

            return resultSubject
        }

        private fun getReferenceFromEnvelope(envelope: TransactionEnvelope): String {
            return envelope.tx.operations
                    .map(Operation::body)
                    .filterIsInstance(Operation.OperationBody.Payment::class.java)
                    .firstOrNull()
                    ?.paymentOp
                    ?.reference
                    ?: throw IllegalArgumentException("Transaction must contain a payment")
        }
    }
}