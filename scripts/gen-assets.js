const sharp = require('sharp');
const fs = require('fs');

fs.mkdirSync('assets', { recursive: true });

(async () => {
  await sharp('resources/icon.svg').resize(1024, 1024).png().toFile('assets/icon.png');
  await sharp('resources/splash.svg').resize(2732, 2732).png().toFile('assets/splash.png');
  console.log('Assets generated');
})();
