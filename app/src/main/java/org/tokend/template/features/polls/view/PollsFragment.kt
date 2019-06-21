package org.tokend.template.features.polls.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_polls.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.features.polls.logic.AddVoteUseCase
import org.tokend.template.features.polls.logic.RemoveVoteUseCase
import org.tokend.template.features.polls.model.PollRecord
import org.tokend.template.features.polls.repository.PollsRepository
import org.tokend.template.features.polls.view.adapter.PollListItem
import org.tokend.template.features.polls.view.adapter.PollsAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.logic.transactions.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.ProgressDialogFactory

class PollsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val allowToolbar: Boolean by lazy {
        arguments?.getBoolean(ALLOW_TOOLBAR_EXTRA, true) ?: true
    }

    private val pollsRepository: PollsRepository
        get() = repositoryProvider.polls()

    private val adapter = PollsAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_polls, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initList()
        subscribeToPolls()
        update()
    }

    // region Init
    private fun initToolbar() {
        if (allowToolbar) {
            toolbar.title = getString(R.string.polls_title)

            ElevationUtil.initScrollElevation(polls_list, appbar_elevation_view)
        } else {
            appbar_elevation_view.visibility = View.GONE
            appbar.visibility = View.GONE
        }
        toolbarSubject.onNext(toolbar)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }

    }

    private fun initList() {
        adapter.onPollAction { item, choice ->
            item.source?.also { poll ->
                if (choice != null) {
                    submitVote(poll, choice)
                } else {
                    removeVote(poll)
                }
            }
        }

        polls_list.adapter = adapter
        polls_list.layoutManager = LinearLayoutManager(requireContext())

        error_empty_view.setEmptyDrawable(R.drawable.ic_poll)
        error_empty_view.observeAdapter(adapter, R.string.no_polls_found)
        error_empty_view.setEmptyViewDenial { pollsRepository.isNeverUpdated }
    }
    // endregion

    private fun update(force: Boolean = false) {
        if (!force) {
            pollsRepository.updateIfNotFresh()
        } else {
            pollsRepository.update()
        }
    }

    private fun subscribeToPolls() {
        pollsRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::displayPolls)
                .addTo(compositeDisposable)

        pollsRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it, "polls") }
                .addTo(compositeDisposable)

        pollsRepository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { error ->
                    if (!adapter.hasData) {
                        error_empty_view.showError(error,
                                errorHandlerFactory.getDefault()) {
                            update(true)
                        }
                    } else {
                        errorHandlerFactory.getDefault().handle(error)
                    }
                }
                .addTo(compositeDisposable)

    }

    private fun displayPolls(polls: List<PollRecord>) {
        adapter.setData(polls.map(::PollListItem))
    }

    // region Voting
    private fun submitVote(poll: PollRecord,
                           choice: Int) {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getTunedDialog(requireContext())
        progress.setMessage(getString(R.string.processing_progress))
        progress.setCancelable(true)
        progress.setOnCancelListener { disposable?.dispose() }

        disposable = AddVoteUseCase(
                pollId = poll.id,
                pollOwnerAccountId = poll.ownerAccountId,
                choiceIndex = choice,
                accountProvider = accountProvider,
                walletInfoProvider = walletInfoProvider,
                repositoryProvider = repositoryProvider,
                txManager = TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    progress.show()
                }
                .doOnTerminate {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = {
                            toastManager.short(R.string.vote_has_been_submitted)
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                        }
                )
                .addTo(compositeDisposable)
    }

    private fun removeVote(poll: PollRecord) {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getTunedDialog(requireContext())
        progress.setMessage(getString(R.string.processing_progress))
        progress.setCancelable(true)
        progress.setOnCancelListener { disposable?.dispose() }

        disposable = RemoveVoteUseCase(
                pollId = poll.id,
                pollOwnerAccountId = poll.ownerAccountId,
                accountProvider = accountProvider,
                walletInfoProvider = walletInfoProvider,
                repositoryProvider = repositoryProvider,
                txManager = TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    progress.show()
                }
                .doOnTerminate {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = {
                            toastManager.short(R.string.vote_has_been_removed)
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                        }
                )
                .addTo(compositeDisposable)
    }
    // endregion

    companion object {
        val ID = "polls".hashCode().toLong() and 0xffff
        private const val ALLOW_TOOLBAR_EXTRA = "allow_toolbar"
        private const val OWNER_ACCOUNT_ID_EXTRA = "owner"

        fun newInstance(allowToolbar: Boolean,
                        ownerAccountId: String?): PollsFragment {
            val fragment = PollsFragment()
            fragment.arguments = Bundle().apply {
                putString(OWNER_ACCOUNT_ID_EXTRA, ownerAccountId)
                putBoolean(ALLOW_TOOLBAR_EXTRA, allowToolbar)
            }
            return fragment
        }
    }
}