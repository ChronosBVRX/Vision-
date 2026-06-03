const puppeteer = require('puppeteer');

const sites = [
  {
    name: 'Cuevana.cz',
    url: 'https://cuevana.cz/',
    selector: '.TPost, .MovieItemM, .item, a[href*="/pelicula/"]'
  },
  {
    name: 'Cuevana3x.xyz',
    url: 'https://cuevana3x.xyz/',
    selector: '.TPost, .MovieItemM, .item, a[href*="/pelicula/"]'
  }
];

async function inspect(site) {
  console.log(`\n================ Inspecting ${site.name} (${site.url}) ================`);
  let browser;
  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36');
    
    await page.goto(site.url, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await new Promise(r => setTimeout(r, 4000));
    
    const htmlInfo = await page.evaluate((sel) => {
      const el = document.querySelector(sel);
      if (!el) return { found: false, html: 'Selector not found' };
      
      const parent = el.parentElement;
      return {
        found: true,
        tagName: el.tagName,
        className: el.className,
        outerHTML: el.outerHTML.slice(0, 1500),
        parentHTML: parent ? parent.outerHTML.slice(0, 2000) : null
      };
    }, site.selector);
    
    console.log(`Selector matched: ${htmlInfo.found}`);
    if (htmlInfo.found) {
      console.log(`Tag: ${htmlInfo.tagName}, Class: ${htmlInfo.className}`);
      console.log(`HTML Snippet:\n${htmlInfo.outerHTML}\n`);
    } else {
      console.log(`Not found: ${htmlInfo.html}`);
    }

  } catch (err) {
    console.error(`Error inspecting ${site.name}:`, err.message);
  } finally {
    if (browser) await browser.close();
  }
}

async function main() {
  for (const s of sites) {
    await inspect(s);
  }
}

main();
