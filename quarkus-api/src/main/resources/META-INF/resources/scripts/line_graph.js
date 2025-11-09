const LineGraph = (function () {

    const CHART_RANGE_MS = 30000;
    const OPTIONS = {
        series: [{
          name: "myMockTimeseries",
          data: []
        }],
        chart: {
          height: 350,
          type: 'line',
          toolbar: {
            show: false
          },
          zoom: {
            enabled: false
          }
        },
        dataLabels: {
          enabled: false
        },
        stroke: {
          curve: 'smooth'
        },
        title: {
          text: 'My data timeseries',
          align: 'left'
        },
        grid: {
          row: {
            colors: ['#f3f3f3', 'transparent'], // takes an array which will be repeated on columns
            opacity: 0.5
          },
        },
        xaxis: {
          type: 'datetime',
          range: CHART_RANGE_MS
        }
    };

    let chart;
    
    let init = function() {
        chart = new ApexCharts(document.querySelector("#chart"), OPTIONS);
        chart.render();
    }
    
    let appendDatapoint = function(point) {
        chart.appendData([{ data: [{ x: point.timestamp, y: point.value}]}]);
    };
    
    let resetSeries = function(timeseries) {
        chart.updateSeries([{data: timeseries}]);
    };
    
    return {
        init,
        appendDatapoint,
        resetSeries
    };
    
})();
