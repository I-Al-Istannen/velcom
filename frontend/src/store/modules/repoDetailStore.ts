import { createModule, mutation, action } from 'vuex-class-component'
import { CommitComparison, MeasurementID, Commit } from '@/store/types'
import axios from 'axios'
import { comparisonFromJson } from '@/util/CommitComparisonJsonHelper'
import { vxm } from '..'

const VxModule = createModule({
  namespaced: 'repoDetailModule',
  strict: false
})

export class RepoDetailStore extends VxModule {
  private _repoHistory: {
    commit: Commit
    comparison: CommitComparison
  }[] = []
  private _selectedRepoId: string = ''
  // Needs to be a primitive so persistence works.
  private _selectedMeasurements: { metric: string; benchmark: string }[] = []
  // Not a real object, needs to be translated so persistence works.
  private _referenceDatapoint: {
    commit: Commit
    comparison: CommitComparison
    measurementId: MeasurementID
  } | null = null
  /**
   * If true the user is locked to the relative commit, if false the
   * relative commit will be ignored.
   *
   * @type {boolean}
   * @memberof RepoDetailStore
   */
  lockedToRelativeCommit: boolean = false
  relativeToCommit: string = ''
  selectedFetchAmount: string = '10'
  selectedSkipAmount: string = '0'

  @action
  async fetchHistoryForRepo(payload: {
    repoId: string
    amount: number
    skip: number
  }): Promise<{ commit: Commit; comparison: CommitComparison }[]> {
    let response = await axios.get('/commit-history', {
      snackbarTag: 'commit-history',
      params: {
        repo_id: payload.repoId,
        amount: payload.amount,
        skip: payload.skip,
        relative_to: this.lockedToRelativeCommit
          ? this.relativeToCommit
          : undefined
      }
    })

    let commitArray: any[] = response.data.commits

    let resultArray: {
      commit: Commit
      comparison: CommitComparison
    }[] = commitArray.map(jsonComparison => {
      const commitComparison: CommitComparison = comparisonFromJson(
        jsonComparison
      )
      return {
        commit: commitComparison.secondCommit,
        comparison: commitComparison
      }
    })

    this.setRepoHistory(resultArray)

    return Promise.resolve(resultArray)
  }

  @action
  dispatchDeleteMeasurements(payload: {
    measurementId: MeasurementID
    repoId: string
  }): Promise<void> {
    return (
      axios
        .delete('/measurements', {
          snackbarTag: 'delete-measurements',
          params: {
            repo_id: payload.repoId,
            benchmark: payload.measurementId.benchmark,
            metric: payload.measurementId.metric
          }
        })
        // udpate repo
        .then(() => {
          return vxm.repoModule.fetchRepoByID(payload.repoId)
        })
        // delete result
        .then(it => {})
    )
  }

  @action
  fetchIndexOfCommit(payload: {
    commitHash: string
    repoId: string
  }): Promise<{ index: number; comparison: CommitComparison }> {
    return axios
      .get('/commit-history', {
        snackbarTag: 'commit-history',
        params: {
          repo_id: payload.repoId,
          amount: 1,
          skip: 0,
          relative_to: payload.commitHash
        }
      })
      .then(it => ({
        index: it.data.offset,
        comparison: comparisonFromJson(it.data.commits[0])
      }))
  }

  @mutation
  setRepoHistory(history: { commit: Commit; comparison: CommitComparison }[]) {
    this._repoHistory = history
  }

  /**
   * Returns the locally stored history for a single repo.
   *
   * Empty array if there is no fetched history.
   *
   * @readonly
   * @memberof RepoDetailStore
   */
  get repoHistory(): { commit: Commit; comparison: CommitComparison }[] {
    return this._repoHistory
  }

  /**
   * Returns all selected measurements.
   *
   * @readonly
   * @type {MeasurementID[]}
   * @memberof RepoDetailStore
   */
  get selectedMeasurements(): MeasurementID[] {
    return this._selectedMeasurements.map(
      ({ metric, benchmark }) => new MeasurementID(benchmark, metric)
    )
  }

  /**
   * Sets the selected measurements.
   *
   * @memberof RepoDetailStore
   */
  set selectedMeasurements(measurements: MeasurementID[]) {
    measurements.forEach(it => {
      if (!it) {
        throw new Error('UNDEFINED OR NULL!')
      }
    })
    this._selectedMeasurements = measurements
  }

  /**
   * Returns the reference datapoint.
   *
   * @readonly
   * @type {({
   *     commit: Commit
   *     comparison: CommitComparison
   *     measurementId: MeasurementID
   *   } | null)}
   * @memberof RepoDetailStore
   */
  get referenceDatapoint(): {
    commit: Commit
    comparison: CommitComparison
    measurementId: MeasurementID
  } | null {
    if (!this._referenceDatapoint) {
      return null
    }
    return {
      commit: Commit.fromRawObject(this._referenceDatapoint.commit),
      comparison: CommitComparison.fromRawObject(
        this._referenceDatapoint.comparison
      ),
      measurementId: MeasurementID.fromRawObject(
        this._referenceDatapoint.measurementId
      )
    }
  }

  /**
   * Sets the reference data point.
   *
   * @memberof RepoDetailStore
   */
  set referenceDatapoint(
    datapoint: {
      commit: Commit
      comparison: CommitComparison
      measurementId: MeasurementID
    } | null
  ) {
    this._referenceDatapoint = datapoint
  }

  /**
   * Returns the id of the repo that is currently selected in the repo detail view.
   *
   * Returns an empty string if none.
   *
   * @readonly
   * @type {string}
   * @memberof RepoDetailStore
   */
  get selectedRepoId(): string {
    return this._selectedRepoId
  }

  /**
   * Sets the id of the repo that is currently selected in the repo detail view.
   *
   * @memberof RepoDetailStore
   */
  set selectedRepoId(selectedRepoId: string) {
    this._selectedRepoId = selectedRepoId
  }
}
