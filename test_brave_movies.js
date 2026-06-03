const puppeteer = require('puppeteer');
const cheerio = require('cheerio');

async function searchBraveForMovies(query) {
  let browser = null;
  const urls = new Set();
  
  try {
    console.log(`Searching Brave for movies: "${query}"`);
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.setViewport({ width: 1280, height: 800 });
    
    const searchUrl = `https://search.brave.com/search?q=${encodeURIComponent(query)}`;
    await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });
    
    const html = await page.content();
    const $ = cheerio.load(html);
    
    $('a').each((i, el) => {
      const href = $(el).attr('href');
      if (!href) return;
      
      let cleanHref = href;
      if (cleanHref.startsWith('/')) {
        cleanHref = new URL(cleanHref, 'https://search.brave.com').toString();
      }
      if (!cleanHref.startsWith('http')) return;
      
      if (
        !cleanHref.includes('brave.com') &&
        !cleanHref.includes('google.com') &&
        !cleanHref.includes('wikipedia') &&
        (cleanHref.endsWith('.m3u') || cleanHref.endsWith('.m3u8') || cleanHref.includes('raw.githubusercontent') || cleanHref.includes('github.com'))
      ) {
        urls.add(cleanHref);
      }
    });
    
  } catch (error) {
    console.error("Puppeteer search error:", error.message);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
  
  return Array.from(urls);
}

async function run() {
  const results = await searchBraveForMovies('"peliculas" español latino m3u github');
  console.log("Found links:");
  console.log(results);
}

run();
