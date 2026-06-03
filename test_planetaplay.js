const puppeteer = require('puppeteer');

(async () => {
  console.log("Launching browser...");
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  try {
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://planetaplay.com/', { waitUntil: 'domcontentloaded', timeout: 30000 });

    const scripts = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('script')).map(s => s.src);
    });
    console.log("Script tags found on planetaplay.com:", scripts);

    const mainScript = scripts.find(src => src && src.includes('/assets/index-') && src.endsWith('.js'));
    if (!mainScript) {
      console.log("No index bundle found!");
      return;
    }
    console.log("Fetching bundle:", mainScript);
    const jsContent = await page.evaluate(async (url) => {
      const r = await fetch(url);
      return r.text();
    }, mainScript);

    console.log("Bundle content length:", jsContent.length);

    let searchPos = 0;
    let blocksFound = 0;
    const allChannels = new Map();

    while (true) {
      const index = jsContent.indexOf('"updatedAt"', searchPos);
      if (index === -1) break;

      let startQuote = -1;
      for (let i = index - 1; i >= index - 1000 && i >= 0; i--) {
        const char = jsContent[i];
        if (char === '`') {
          startQuote = i;
          break;
        }
        if (char === '"' && jsContent[i+1] === '{') {
          startQuote = i;
          break;
        }
      }

      if (startQuote !== -1) {
        const quoteChar = jsContent[startQuote];
        let endQuote = -1;
        for (let i = startQuote + 1; i < jsContent.length; i++) {
          if (jsContent[i] === quoteChar && jsContent[i-1] !== '\\') {
            endQuote = i;
            break;
          }
        }

        if (endQuote !== -1) {
          const jsonStr = jsContent.slice(startQuote + 1, endQuote);
          try {
            const unescaped = jsonStr.replace(/\\"/g, '"').replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t');
            const parsed = JSON.parse(unescaped);
            if (parsed.items && Array.isArray(parsed.items)) {
              const tvs = parsed.items.filter(item => item.type === 'tv');
              if (tvs.length > 0) {
                blocksFound++;
                console.log(`Block ${blocksFound} found: contains ${tvs.length} tv items.`);
                tvs.forEach(ch => {
                  allChannels.set(ch.id, ch);
                });
              }
            }
          } catch (e) {
            // ignore
          }
        }
      }
      searchPos = index + 11;
    }

    console.log(`Summary: Found ${blocksFound} blocks of channels. Total unique TV channels: ${allChannels.size}`);
    
    const channelsArray = Array.from(allChannels.values());
    console.log("First 10 channels:");
    console.log(channelsArray.slice(0, 10).map(c => ({ id: c.id, name: c.name, meta: c.meta })));
    
    console.log("Unique metas (categories):", [...new Set(channelsArray.map(c => c.meta))]);

  } catch (err) {
    console.error("Error:", err);
  } finally {
    await browser.close();
  }
})();
