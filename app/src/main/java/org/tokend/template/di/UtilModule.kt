package org.tokend.template.di

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Module
import dagger.Provides
import org.tokend.sdk.factory.JsonApiToolsProvider
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.util.comparator.*
import org.tokend.template.util.errorhandler.DefaultErrorLogger
import org.tokend.template.util.errorhandler.ErrorHandlerFactory
import org.tokend.template.util.errorhandler.ErrorLogger
import org.tokend.template.util.locale.AppLocaleManager
import org.tokend.template.view.ToastManager
import org.tokend.template.view.util.formatter.AmountFormatter
import org.tokend.template.view.util.formatter.DefaultAmountFormatter
import javax.inject.Singleton

@Module
class UtilModule {
    @Provides
    @Singleton
    fun errorHandlerFactory(context: Context,
                            localeManager: AppLocaleManager,
                            toastManager: ToastManager,
                            errorLogger: ErrorLogger): ErrorHandlerFactory {
        return ErrorHandlerFactory(localeManager.getLocalizeContext(context),
                toastManager, errorLogger)
    }

    @Provides
    @Singleton
    fun toastManager(context: Context,
                     localeManager: AppLocaleManager): ToastManager {
        return ToastManager(localeManager.getLocalizeContext(context))
    }

    @Provides
    @Singleton
    fun assetCodeComparator(): Comparator<String> {
        return AssetCodeComparator()
    }

    @Provides
    @Singleton
    fun assetComparator(): Comparator<Asset> {
        return AssetComparator(assetCodeComparator())
    }

    @Provides
    @Singleton
    fun balanceComparator(): Comparator<BalanceRecord> {
        return BalancesByBaseAssetComparator(
                BalancesByAmountExistenceComparator(
                        BalancesByAssetCodeComparator(
                                assetCodeComparator()
                        )
                )
        )
    }

    @Provides
    @Singleton
    fun amountFormatter(): AmountFormatter {
        return DefaultAmountFormatter()
    }

    @Provides
    @Singleton
    fun objectMapper(): ObjectMapper {
        return JsonApiToolsProvider.getObjectMapper()
    }

    @Provides
    @Singleton
    fun errorLogger(): ErrorLogger {
        return DefaultErrorLogger()
    }
}