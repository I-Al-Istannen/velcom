<template>
  <v-row no-gutters>
    <v-col>
      <v-select
        class="mr-5"
        :items="occuringBenchmarks"
        :value="selectedMeasurement.benchmark"
        @input="selectBenchmark"
        label="benchmark"
      ></v-select>
    </v-col>
    <v-col>
      <v-select
        class="mr-5"
        :items="metricsForBenchmark(selectedMeasurement.benchmark)"
        :value="selectedMeasurement.metric"
        @input="selectMetric"
        label="metric"
      ></v-select>
    </v-col>
  </v-row>
</template>

<script lang="ts">
import Vue from 'vue'
import Component from 'vue-class-component'
import { vxm } from '../../store'
import { Prop, Watch } from 'vue-property-decorator'
import { MeasurementID } from '../../store/types'

@Component
export default class MeasurementIdSelection extends Vue {
  @Prop()
  private repoId!: string

  @Prop()
  private selectedMeasurement!: MeasurementID

  get occuringBenchmarks(): string[] {
    return vxm.repoModule.occuringBenchmarks([this.repoId])
  }

  get metricsForBenchmark(): (benchmark: string) => string[] {
    return (benchmark: string) => vxm.repoModule.metricsForBenchmark(benchmark)
  }

  @Watch('selectedMeasurement')
  private onMeasurementChange() {
    let newMetrics = this.metricsForBenchmark(
      this.selectedMeasurement.benchmark
    )

    if (!newMetrics.includes(this.selectedMeasurement.metric)) {
      if (newMetrics) {
        this.selectMetric(newMetrics[0])
      }
    }
  }

  private selectBenchmark(benchmark: string) {
    this.$emit(
      'changeMeasurement',
      new MeasurementID(benchmark, this.selectedMeasurement.metric)
    )
  }

  private selectMetric(metric: string) {
    this.$emit(
      'changeMeasurement',
      new MeasurementID(this.selectedMeasurement.benchmark, metric)
    )
  }
}
</script>

<style scoped>
</style>