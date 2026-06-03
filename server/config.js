// Configuration for custom video search sources
module.exports = {
  customSearchSources: [
    {
      name: 'VerSeries',
      searchUrl: 'https://ver-series.com/search?q={{query}}',
      usePlaywright: true // Page requires JS to load results
    }
    // Add more sources as needed, e.g.
    // {
    //   name: 'Cuevana',
    //   searchUrl: 'https://cuevana3x.xyz/search?q={{query}}',
    //   usePlaywright: false
    // }
  ]
};
