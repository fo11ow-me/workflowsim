<template>
  <div class="gantt-chart">
    <div class="header">
      <div class="file-input">
        <label for="jsonFile">
          <span class="icon">📁</span>
          <span>select sim file</span>
        </label>
        <input
            type="file"
            id="jsonFile"
            @change="handleFileUpload"
            accept=".json"
        >
        <span class="file-name">{{ fileName }}</span>
      </div>
    </div>
    <div class="chart-container" ref="gstc"></div>
  </div>
</template>

<script>
import GSTC from "gantt-schedule-timeline-calendar";
import {Plugin as TimelinePointer} from "gantt-schedule-timeline-calendar/dist/plugins/timeline-pointer.esm.min.js";
import {Plugin as Selection} from "gantt-schedule-timeline-calendar/dist/plugins/selection.esm.min.js";
import {Plugin as ItemResizing} from "gantt-schedule-timeline-calendar/dist/plugins/item-resizing.esm.min.js";
import {Plugin as ItemMovement} from "gantt-schedule-timeline-calendar/dist/plugins/item-movement.esm.min.js";
import {Plugin as Bookmarks} from "gantt-schedule-timeline-calendar/dist/plugins/time-bookmarks.esm.min.js";
import {Plugin as DependencyLines} from "gantt-schedule-timeline-calendar/dist/plugins/dependency-lines.esm.min.js";
import "gantt-schedule-timeline-calendar/dist/style.css";
import * as FloatingUIDOM from '@floating-ui/dom';

/**
 * @type {import("../../dist/gstc").ChartCalendarLevel}
 */
const hours = [
  {
    zoomTo: 100, // we want to display this format for all zoom levels until 100
    period: 'hour',
    periodIncrement: 1,
    format({timeStart}) {
      return timeStart.format('HH:mm DD MMMM YYYY'); // full list of formats: https://day.js.org/docs/en/display/format
    },
  },
];

/**
 * @type {import("../../dist/gstc").ChartCalendarLevel}
 */
const minutes = [
  {
    zoomTo: 100, // we want to display this format for all zoom levels until 100
    period: 'minute',
    periodIncrement: 10,
    main: true,
    format({timeStart, vido}) {
      return vido.html`<div style="text-align:center;">${timeStart.format('HH:mm')}</div>`; // full list of formats: https://day.js.org/docs/en/display/format
    },
  },
];

export default {
  name: "GanttChart",
  data() {
    return {
      gstc: null,
      state: null,
      fileName: 'No file selected',
      config: {
        licenseKey:
            "====BEGIN LICENSE KEY====\nXOfH/lnVASM6et4Co473t9jPIvhmQ/l0X3Ewog30VudX6GVkOB0n3oDx42NtADJ8HjYrhfXKSNu5EMRb5KzCLvMt/pu7xugjbvpyI1glE7Ha6E5VZwRpb4AC8T1KBF67FKAgaI7YFeOtPFROSCKrW5la38jbE5fo+q2N6wAfEti8la2ie6/7U2V+SdJPqkm/mLY/JBHdvDHoUduwe4zgqBUYLTNUgX6aKdlhpZPuHfj2SMeB/tcTJfH48rN1mgGkNkAT9ovROwI7ReLrdlHrHmJ1UwZZnAfxAC3ftIjgTEHsd/f+JrjW6t+kL6Ef1tT1eQ2DPFLJlhluTD91AsZMUg==||U2FsdGVkX1/SWWqU9YmxtM0T6Nm5mClKwqTaoF9wgZd9rNw2xs4hnY8Ilv8DZtFyNt92xym3eB6WA605N5llLm0D68EQtU9ci1rTEDopZ1ODzcqtTVSoFEloNPFSfW6LTIC9+2LSVBeeHXoLEQiLYHWihHu10Xll3KsH9iBObDACDm1PT7IV4uWvNpNeuKJc\npY3C5SG+3sHRX1aeMnHlKLhaIsOdw2IexjvMqocVpfRpX4wnsabNA0VJ3k95zUPS3vTtSegeDhwbl6j+/FZcGk9i+gAy6LuetlKuARjPYn2LH5Be3Ah+ggSBPlxf3JW9rtWNdUoFByHTcFlhzlU9HnpnBUrgcVMhCQ7SAjN9h2NMGmCr10Rn4OE0WtelNqYVig7KmENaPvFT+k2I0cYZ4KWwxxsQNKbjEAxJxrzK4HkaczCvyQbzj4Ppxx/0q+Cns44OeyWcwYD/vSaJm4Kptwpr+L4y5BoSO/WeqhSUQQ85nvOhtE0pSH/ZXYo3pqjPdQRfNm6NFeBl2lwTmZUEuw==\n====END LICENSE KEY====",
        plugins: [TimelinePointer(), Selection(), ItemResizing(), ItemMovement(), Bookmarks(), DependencyLines()],
        innerHeight: 700,
        list: {
          columns: {
            data: {},
          },
          rows: {},
          row: {
            height: 80
          }
        },
        chart: {
          items: {},
          calendarLevels: [hours, minutes],
          time: {
            zoom: 13,
            from: 0,
            to: 0,
          },
        },
        actions: {
          'chart-timeline-items-row-item': [this.itemAction],
          // 'list-column-row': [this.rowAction],
          // 'chart-calendar-date': [this.dateAction],
        },
        slots: {
          main: {outer: [this.mainSlotWithTooltip]},
        },
      },
      colors: ['#E74C3C', '#DA3C78', '#7E349D', '#0077C0', '#07ABA0', '#0EAC51', '#F1892D'],
      floatingUI: FloatingUIDOM,
      disableTooltip: false,
      tooltipContent: ''
    };
  },

  methods: {
    handleFileUpload(event) {
      const file = event.target.files[0];
      this.fileName = file.name;
      const reader = new FileReader();
      reader.onload = e => {
        try {
          const columns = [
            {
              id: "id",
              data: ({row}) => GSTC.api.sourceID(row.index),  // show original id (not internal GSTCID)
              width: 60,
              header: {
                content: "Idx",
              },
            },
            {
              id: "label",
              data: "label",
              width: 100,
              header: {
                content: "Vm",
              },
            },
          ]
          const jsonData = JSON.parse(e.target.result);
          let uniqueVmIds = Array.from(new Set(jsonData.map(item => item.vmId)));
          uniqueVmIds.sort((x, y) => x - y);
          const rows = uniqueVmIds.map((vmId, i) => ({
            id: vmId,
            label: `Vm #${vmId}`,
            index: i
          }));
          const items = jsonData.map(item => ({
            id: item.id,
            label: item.name,
            rowId: item.vmId,
            time: {
              start: GSTC.api.date(item.startTime).valueOf(),
              end: GSTC.api.date(item.endTime).valueOf(),
            },
            dependant: item.childList.map(childId => GSTC.api.GSTCID(childId)),
            style: {background: this.colors[item.vmId % this.colors.length]},
            depth: item.depth
          }));
          const allTimes = items.flatMap(item => [item.time.start, item.time.end]);
          const start = Math.min(...allTimes);
          const end = Math.max(...allTimes);
          this.config.list.columns.data = GSTC.api.fromArray(columns)
          this.config.list.rows = GSTC.api.fromArray(rows)
          this.config.chart.items = GSTC.api.fromArray(items)
          this.config.chart.time.from = start;
          this.config.chart.time.to = end;
          this.state = GSTC.api.stateFromConfig(this.config);
          this.gstc = GSTC({
            element: this.$refs.gstc,
            state: this.state,
          });
        } catch (error) {
          console.error('data read error:', error);
        }
      };
      reader.readAsText(file);
    },
    mainSlotWithTooltip(vido) {
      const {html} = vido;
      return (content) =>
          html`${content}
          <div id="tooltip" role="tooltip">
            ${this.tooltipContent}
            <div id="tooltip-arrow"></div>
          </div>
          <style>
            #tooltip {
              display: none;
              width: max-content;
              position: absolute;
              top: 0;
              left: 0;
              background: #222;
              color: white;
              font-weight: bold;
              padding: 5px;
              border-radius: 4px;
              font-size: 90%;
            }

            #tooltip-arrow {
              position: absolute;
              background: #222;
              width: 8px;
              height: 8px;
              transform: rotate(45deg);
            }
          </style>`;
    },
    async showTooltip(element, content) {
      this.tooltipContent = content;
      if (this.disableTooltip) {
        this.hideTooltip();
        return;
      }
      // we need to refresh component to trigger slot update with our new content
      await this.gstc.component.update();

      const tooltip = document.getElementById('tooltip');
      const arrowElement = document.getElementById('tooltip-arrow');
      if (!tooltip || !arrowElement) return;
      tooltip.style.display = 'block';

      const {x, y, placement, middlewareData} = await this.floatingUI.computePosition(element, tooltip, {
        placement: 'top',
        middleware: [
          this.floatingUI.flip(),
          this.floatingUI.shift(),
          this.floatingUI.offset(6),
          this.floatingUI.arrow({element: arrowElement}),
        ],
      });

      Object.assign(tooltip.style, {
        left: `${x}px`,
        top: `${y}px`,
        display: 'block',
      });

      const {x: arrowX, y: arrowY} = middlewareData.arrow;
      const staticSide = {
        top: 'bottom',
        right: 'left',
        bottom: 'top',
        left: 'right',
      }[placement.split('-')[0]];

      Object.assign(arrowElement.style, {
        left: arrowX != null ? `${arrowX}px` : '',
        top: arrowY != null ? `${arrowY}px` : '',
        right: '',
        bottom: '',
        [staticSide]: '-4px',
      });
    },
    hideTooltip() {
      const tooltip = document.getElementById('tooltip');
      if (tooltip) tooltip.style.display = 'none';
    },
    itemAction(element, data) {
      let itemTooltipContent = () =>
          GSTC.lithtml.html`
          <div>ID: ${GSTC.api.sourceID(data.item.id)}</div>
          <div>Name: ${data.item.label}</div>
          <div>Depth: ${data.item.depth}</div>
          <div>From: ${data.itemData.time.startDate.format('YYYY-MM-DD HH:mm:ss')}</div>
          <div>To:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;${data.itemData.time.endDate.format('YYYY-MM-DD HH:mm:ss')}</div>
      `;

      const showTooltipEventListener = () => this.showTooltip(element, itemTooltipContent());
      const hideTooltipEventListener = () => this.hideTooltip();

      element.addEventListener('mouseenter', showTooltipEventListener);
      element.addEventListener('mousemove', showTooltipEventListener);
      element.addEventListener('mouseleave', hideTooltipEventListener);
      element.addEventListener('click', showTooltipEventListener);

      return {
        update(_element, updatedData) {
          data = updatedData;
        },
        // eslint-disable-next-line no-unused-vars
        destroy(element, _data) {
          this.hideTooltip();
          element.removeEventListener('mouseenter', showTooltipEventListener);
          element.removeEventListener('mousemove', showTooltipEventListener);
          element.removeEventListener('mouseleave', hideTooltipEventListener);
          element.removeEventListener('click', showTooltipEventListener);
        },
      };
    },

    rowAction(element, data) {
      let itemTooltipContent = () =>
          GSTC.lithtml.html`<div>ID: ${GSTC.api.sourceID(data.row.id)}</div><div>Name: ${data.row.label}</div>`;

      const showTooltipEventListener = () => this.showTooltip(element, itemTooltipContent());
      const hideTooltipEventListener = () => this.hideTooltip();

      element.addEventListener('mouseenter', showTooltipEventListener);
      element.addEventListener('mousemove', showTooltipEventListener);
      element.addEventListener('mouseleave', hideTooltipEventListener);
      element.addEventListener('click', showTooltipEventListener);

      return {
        update(_element, updatedData) {
          data = updatedData;
        },
        // eslint-disable-next-line no-unused-vars
        destroy(element, _data) {
          this.hideTooltip();
          element.removeEventListener('mouseenter', showTooltipEventListener);
          element.removeEventListener('mousemove', showTooltipEventListener);
          element.removeEventListener('mouseleave', hideTooltipEventListener);
          element.removeEventListener('click', showTooltipEventListener);
        },
      };
    },
    dateAction(element, data) {

      let itemTooltipContent = () => GSTC.lithtml.html`<div>Date: ${data.date.leftGlobalDate.format('YYYY-MM-DD')}</div>`;

      const showTooltipEventListener = () => this.showTooltip(element, itemTooltipContent());
      const hideTooltipEventListener = () => this.hideTooltip();

      element.addEventListener('mouseenter', showTooltipEventListener);
      element.addEventListener('mousemove', showTooltipEventListener);
      element.addEventListener('mouseleave', hideTooltipEventListener);
      element.addEventListener('click', showTooltipEventListener);

      return {
        update(_element, updatedData) {
          data = updatedData;
        },
        // eslint-disable-next-line no-unused-vars
        destroy(element, _data) {
          this.hideTooltip();
          element.removeEventListener('mouseenter', showTooltipEventListener);
          element.removeEventListener('mousemove', showTooltipEventListener);
          element.removeEventListener('mouseleave', hideTooltipEventListener);
          element.removeEventListener('click', showTooltipEventListener);
        },
      };
    }
  },

};
</script>
<style scoped>
.gantt-chart {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden; /* 新增 */
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.2rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.header h2 {
  margin: 0;
  color: var(--text-color);
  font-size: 1.5rem;
}

.chart-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
