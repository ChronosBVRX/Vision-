const axios = require('axios');
const cheerio = require('cheerio');

async function test() {
  try {
    const query = "roja directa";
    const url = `https://search.brave.com/search?q=${encodeURIComponent(query)}`;
    
    console.log(`Fetching Brave Search: ${url}`);
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
      },
      timeout: 6000
    });
    
    console.log("Status:", response.status);
    const $ = cheerio.load(response.data);
    
    const results = new Set();
    
    // Look at all anchor tags
    $('a').each((i, el) => {
      const href = $(el).attr('href');
      if (!href || !href.startsWith('http')) return;
      
      try {
        const parsed = new URL(href);
        const host = parsed.hostname;
        
        // Filter out search engine and common domains
        if (
          !host.includes('brave.com') &&
          !host.includes('google.com') &&
          !host.includes('wikipedia') &&
          !host.includes('twitter.com') &&
          !host.includes('facebook.com') &&
          !host.includes('youtube.com') &&
          !host.includes('instagram.com') &&
          !host.includes('microsoft.com') &&
          !host.includes('apple.com') &&
          !host.includes('pinterest.com')
        ) {
          results.add(`${parsed.protocol}//${host}`);
        }
      } catch (e) {}
    });
    
    console.log("Discovered domains:");
    console.log(Array.from(results));
    
  } catch (error) {
    console.error("Error fetching Brave Search:", error.message);
  }
}

test();
