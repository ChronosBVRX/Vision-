const axios = require('axios');
const cheerio = require('cheerio');

async function test() {
  const url = 'https://www.pelisplushd.la/serie/the-boroughs-jubilacion-rebelde/temporada/1/capitulo/1';
  const response = await axios.get(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    }
  });
  const $ = cheerio.load(response.data);
  
  const options = [];
  $('.VideoPlayer ul li').each((i, el) => {
    const name = $(el).text().trim();
    const dataId = $(el).attr('data-id');
    const span = $(`#link_url span[lid="${dataId}"]`);
    const streamUrl = span.attr('url');
    console.log("Server:", name, "URL:", streamUrl);
    if (streamUrl) options.push({ name, url: streamUrl });
  });
  
  console.log("Total options extracted:", options.length);
}
test();
