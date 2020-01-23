<template>
  <v-container>
    <v-data-iterator
      :items="runs"
      :hide-default-footer="runs.length < defaultItemsPerPage"
      :items-per-page="defaultItemsPerPage"
      :footer-props="{ itemsPerPageOptions: itemsPerPageOptions }"
    >
      <template v-slot:default="props">
        <v-row>
          <v-col cols="12" class="my-1 py-0" v-for="(run, index) in props.items" :key="index">
            <v-card>
              <v-list-item>
                <v-list-item-content>
                  <v-container fluid>
                    <v-row no-gutters align="center">
                      <v-col cols="8">
                        <v-list-item-title>
                          <repo-display :repoId="run.commit.repoID"></repo-display>
                          <span class="mx-2">—</span>
                          <router-link
                            class="ml-3 mx-auto"
                            :to="{ name: 'commit-detail', params: { repoID: run.commit.repoID, hash: run.commit.hash } }"
                            tag="button"
                          >
                            <span class="commit-message">{{ run.commit.message }}</span>
                          </router-link>
                        </v-list-item-title>
                        <v-list-item-subtitle>
                          <span class="author">{{ run.commit.author }}</span> authored on
                          <span
                            class="time"
                            :title="formatDateUTC(run.commit.authorDate)"
                          >{{ formatDate(run.commit.authorDate) }}</span>
                        </v-list-item-subtitle>
                      </v-col>
                      <v-col>
                        <v-container fluid class="ma-0 pa-0">
                          <v-row no-gutters align="center" justify="space-between">
                            <v-col>
                              <v-chip
                                outlined
                                label
                                color="accent"
                                class="commit-hash-chip"
                                @click="copyToClipboard(run.commit.hash)"
                              >{{ run.commit.hash }}</v-chip>
                            </v-col>
                            <span></span>
                          </v-row>
                        </v-container>
                      </v-col>
                    </v-row>
                  </v-container>
                </v-list-item-content>
              </v-list-item>
            </v-card>
          </v-col>
        </v-row>
      </template>
    </v-data-iterator>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue'
import Component from 'vue-class-component'
import { Prop, Watch } from 'vue-property-decorator'
import { vxm } from '@/store/index'
import { Commit, Run } from '@/store/types'
import InlineMinimalRepoNameDisplay from '../InlineMinimalRepoDisplay.vue'
import { mdiRocket, mdiDelete } from '@mdi/js'

@Component({
  components: {
    'repo-display': InlineMinimalRepoNameDisplay
  }
})
export default class RunOverview extends Vue {
  @Prop({})
  private runs!: Run[]

  private itemsPerPageOptions: number[] = [10, 20, 50, 100, 200, -1]
  private defaultItemsPerPage: number = 20

  private formatDate(date: number): string {
    let myDate = this.getDate(date)

    return myDate.toLocaleString()
  }

  private formatDateUTC(date: number): string {
    let myDate = this.getDate(date)

    return myDate.toUTCString()
  }

  private getDate(date: number): Date {
    let myDate = new Date()
    // TODO: remove clamping
    myDate.setTime((Math.abs(date) % 1.8934156e9) * 1000)
    return myDate
  }

  private copyToClipboard(hash: string) {
    let selection = window.getSelection()
    if (selection && selection.toString() !== '') {
      // Do not overwrite user text selection
      return
    }
    navigator.clipboard
      .writeText(hash)
      .then(it => this.$globalSnackbar.setSuccess('Copied!'))
      .catch(error =>
        this.$globalSnackbar.setError('Could not copy to clipboard :( ' + error)
      )
  }
}
</script>