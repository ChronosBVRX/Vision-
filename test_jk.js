const https = require('https');
https.get('https://jkanime.net/directorio?tipo=peliculas', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    // Find where the movies are listed
    const cards = data.match(/<a href="([^"]+)"[^>]*>[\s\S]*?<h5[^>]*>([^<]+)<\/h5>/g);
    if (cards) {
       console.log("Found cards with <h5>:", cards.slice(0,2));
    } else {
       console.log("No <h5> found in links. Snippet:", data.substring(2000, 3000));
       const titles = data.match(/title="([^"]+)"/g);
       console.log("Titles found:", titles ? titles.slice(0, 10) : "none");
    }
  });
});
