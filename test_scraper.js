const puppeteer = require('puppeteer');

async function test() {
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  const page = await browser.newPage();
  await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36');

  try {
    console.log("Visitando AnimeOnline.ninja...");
    await page.goto('https://ww3.animeonline.ninja/pelicula/', { waitUntil: 'domcontentloaded', timeout: 15000 });
    const ninjaHtml = await page.evaluate(() => document.body.innerHTML.substring(0, 500));
    const title = await page.title();
    console.log("Title Ninja:", title);
    console.log("HTML Ninja:", ninjaHtml);
  } catch (e) { console.error("Error ninja:", e.message); }

  try {
    console.log("Visitando JKAnime.net...");
    await page.goto('https://jkanime.net/directorio?tipo=peliculas&page=1', { waitUntil: 'domcontentloaded', timeout: 15000 });
    const jkHtml = await page.evaluate(() => {
      const cards = document.querySelectorAll('.anime__item, .custom_item2, .contai, article');
      if (cards.length > 0) return cards[0].outerHTML;
      const links = Array.from(document.querySelectorAll('a')).map(a => a.href).filter(h => h.includes('jkanime.net'));
      return "No cards found. Links: " + links.slice(0, 10).join(', ');
    });
    console.log("HTML JK First Card/Links:", jkHtml);
  } catch (e) { console.error("Error JK:", e.message); }

  await browser.close();
}
test();
