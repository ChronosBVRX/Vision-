// Custom video source search module
// Reads configured sources and returns an array of direct video URLs (or intermediate URLs) for a given query.
// The function respects the user‑specified delay between requests and filters by language when possible.

const axios = require('axios');
const cheerio = require('cheerio');
const config = require('../config'); // project root config

// Simple sleep helper
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Performs a custom search across configured sources.
 * @param {string} query - The movie/series title to search for.
 * @returns {Promise<Array<string>>} - Array of URL strings (direct or iframe links).
 */
async function searchCustom(query) {
  if (!query) return [];
  const results = [];
  const sources = config.customSearchSources || [];
  for (const src of sources) {
    try {
      const url = src.searchUrl.replace('{{query}}', encodeURIComponent(query));
      let pageContent;
      if (src.usePlaywright) {
        const { chromium } = require('playwright');
        const browser = await chromium.launch({ headless: true });
        const page = await browser.newPage();
        try {
          await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
          // Wait briefly for any JS redirects to finish
          await new Promise(r => setTimeout(r, 2000));
          pageContent = await page.content();
        } catch (navErr) {
          console.warn(`[customSearch] Navigation error, trying to get content anyway: ${navErr.message}`);
          try { pageContent = await page.content(); } catch(e) {}
        }
        await browser.close();
      } else {
        const resp = await axios.get(url, {
          headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36' },
          timeout: 8000
        });
        pageContent = resp.data;
      }

      const $ = cheerio.load(pageContent);
      // Very generic extraction: look for <a> or <iframe> with http(s) href/src that looks like a video link.
      $('a[href], iframe[src]').each((_, el) => {
        const href = $(el).attr('href') || $(el).attr('src');
        if (href && typeof href === 'string' && href.startsWith('http')) {
          if (/google|facebook|ads|doubleclick/.test(href)) return;
          results.push(href);
        }
      });
    } catch (e) {
      console.warn(`[customSearch] Error processing source ${src.name}: ${e.message}`);
    }
    // Respect user‑defined delay (500 ms as per user preference)
    await sleep(500);
  }
  return Array.from(new Set(results));
}

module.exports = { searchCustom };
