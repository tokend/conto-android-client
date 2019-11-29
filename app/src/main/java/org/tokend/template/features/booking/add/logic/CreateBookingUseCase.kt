package org.tokend.template.features.booking.add.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.booking.model.CreateBookingRequestAttributes
import org.tokend.sdk.api.integrations.booking.model.generated.resources.BookingResource
import org.tokend.sdk.api.integrations.escrow.params.CreateEscrowParams
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceInvoiceData
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.booking.model.BookingBusinessRecord
import org.tokend.template.features.booking.model.BookingTime

/**
 * Creates booking and invoice to pay for it.
 */
class CreateBookingUseCase(
        private val time: BookingTime,
        private val roomId: String,
        private val seatsCount: Int,
        private val repositoryProvider: RepositoryProvider,
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider
) {
    private lateinit var business: BookingBusinessRecord
    private lateinit var booking: BookingResource
    private lateinit var paymentSubject: String
    private lateinit var paymentAccount: String

    fun perform(): Single<MarketplaceInvoiceData.Redirect> {
        return getBusiness()
                .doOnSuccess { business ->
                    this.business = business
                }
                .flatMap {
                    createBooking()
                }
                .doOnSuccess { booking ->
                    this.booking = booking
                }
                .flatMap {
                    getPaymentSubject()
                }
                .doOnSuccess { paymentSubject ->
                    this.paymentSubject = paymentSubject
                }
                .flatMap {
                    getPaymentAccount()
                }
                .doOnSuccess { paymentAccount ->
                    this.paymentAccount = paymentAccount
                }
                .flatMap {
                    getInvoice()
                }
    }

    private fun getBusiness(): Single<BookingBusinessRecord> {
        return repositoryProvider
                .bookingBusiness()
                .ensureItem()
    }

    private fun createBooking(): Single<BookingResource> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        return signedApi
                .integrations
                .booking
                .createBooking(
                        businessId = business.id,
                        attributes = CreateBookingRequestAttributes(
                                sourceAccount = accountId,
                                participantsCount = seatsCount,
                                startTime = time.from,
                                endTime = time.to,
                                payload = roomId,
                                type = 0,
                                details = Any(),
                                eventId = null
                        )
                )
                .toSingle()
    }

    private fun getPaymentSubject(): Single<String> {
        val intBookingId = booking.id.toIntOrNull()
                ?: return Single.error(IllegalStateException("Booking ID ${booking.id} " +
                        "can't be casted to int"))

        return GsonFactory().getBaseGson().toJson(mapOf(
                "booking_id" to intBookingId,
                "reference" to booking.reference
        )).toSingle()
    }

    private fun getPaymentAccount(): Single<String> {
        return apiProvider.getApi()
                .integrations
                .booking
                .getPaymentAccountId()
                .toSingle()
    }

    private fun getInvoice(): Single<MarketplaceInvoiceData.Redirect> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        val walletInfo = walletInfoProvider.getWalletInfo()
                ?: return Single.error(IllegalStateException("No wallet info found"))

        return signedApi
                .integrations
                .escrow
                .create(CreateEscrowParams(
                        paymentMethodId = business.paymentMethodId,
                        sourceAccount = walletInfo.accountId,
                        sourceEmail = walletInfo.email,
                        amount = booking.amount,
                        asset = booking.asset.id,
                        destAccount = paymentAccount,
                        subject = paymentSubject,
                        destEmail = null
                ))
                .toSingle()
    }
}