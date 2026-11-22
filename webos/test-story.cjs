const assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const parse = require('./service/protocol').parse;
class Element {
    constructor() { this.style={};this.hidden=true;this.children=[];this.value=''; }
    set textContent(t) { this.value=String(t);this.children=[]; }
    get textContent() { return this.value+this.children.map(c=>c.textContent).join(''); }
    appendChild(c) { this.children.push(c); }
}
const elements=Object.fromEntries(['subtitle','choices','choice-frame','ocr-frame'].map(k=>[k,new Element()]));
const document={getElementById:id=>elements[id],createElement:()=>new Element(),createTextNode:t=>({textContent:t})};
const window={};vm.runInNewContext(fs.readFileSync(__dirname+'/app/receiver-ui.js','utf8'),{window});
const ui=new window.PlayCaptionReceiverUI(document), sub=elements.subtitle;
Object.defineProperty(sub,'clientHeight',{get:()=>40});
Object.defineProperty(sub,'scrollHeight',{get:()=>Math.ceil(Array.from(sub.textContent).length/22)*20});
const root='a'.repeat(32);
function packet(n,text,pause=false,g=1){return parse(Buffer.from(`PCS1\t${root}.${g}.${n}.0\t${pause?1:0}\t${text}\t0.5\t0.91\t0.8\t1\t-65536`));}
assert.equal(parse(Buffer.from(`PCQ1\t${root}.1.0.0`)).kind,'storyQuery');
for(const p of ['PCQ1\tx','PCS1\tx\t0\ttext',`PCQ1\t${root}.1.0.0\textra`])assert.equal(parse(Buffer.from(p)),null);
const full='Trevor: Musíme najít cestu domů. Potom se vrátíme pro ostatní. Až bude klid, zavolej mi.';
const p=packet(1,full);assert.equal(ui.story(p,0),'reading');
let now=0;const pages=[];
while(!sub.hidden){
 const text=sub.textContent;pages.push(text);
 const ms=Math.max(1800,Math.ceil(Array.from(text.normalize('NFC')).length*1000/14));
 assert.equal(ui.story(p,now+ms-1),'reading');ui.tick(now+ms-1);assert.equal(sub.textContent,text);
 now+=ms;ui.tick(now);
 assert.ok(pages.length<30);
}
assert.equal(pages.join(' ').replace(/\s+/g,' ').trim(),full);
assert.equal(ui.story(p,now+1000),'done','Ztracené ACK musí jít zopakovat bez restartu čtení.');
assert.equal(sub.hidden,true);
const q=packet(2,'Počkej.');ui.story(q,now+2000);ui.tick(now+2500);
ui.story(packet(2,'Počkej.',true),now+2500);assert.equal(sub.hidden,true);
ui.tick(now+20000);ui.story(q,now+20000);assert.equal(sub.hidden,false);
ui.tick(now+21299);assert.equal(sub.hidden,false);ui.tick(now+21300);assert.equal(sub.hidden,true);
ui.story(packet(3,'Poslední.'),now+22000);ui.pauseStory(now+22500);
ui.story(packet(3,'Poslední.'),now+30000);ui.tick(now+31299);assert.equal(sub.hidden,false);
ui.tick(now+31300);assert.equal(sub.hidden,true);
ui.story(packet(0,'',true,2),now+32000);assert.equal(ui.storyActive(),false);
assert.equal(ui.oldStory(p.id),true,'Starý paket po přepnutí do Standardního režimu nesmí obnovit titulek.');
ui.caption(parse(Buffer.from('PCT1\t10\t6000\tStandardní titulek\t')),now+32001);
assert.equal(sub.textContent,'Standardní titulek');
ui.story(packet(1,'Nedočtené.',false,3),now+33000);ui.clear();
assert.equal(ui.story(packet(1,'Nedočtené.',false,3),now+34000),'reading','Restart nesmí potvrdit nedočtenou repliku.');
ui.tick(now+35800);ui.clear();
assert.equal(ui.story(packet(1,'Nedočtené.',false,3),now+36000),'done','Dočtená replika se po restartu neopakuje.');
function percentPacket(n,text,percent) {
    return parse(Buffer.from(`PCS1\t${root}.4.${n}.0\t0\t${text}\t0.5\t0.91\t0.8\t1\t-65536\tD:${percent}`));
}
for(const percent of ['0','49','201','NaN','80.5'])assert.equal(percentPacket(1,'Test.',percent),null);
const timed=new window.PlayCaptionReceiverUI(document);
for(const [n,percent,ms] of [[1,100,1200],[2,80,960],[3,120,1440]]) {
    const p=percentPacket(n,'Ano.',percent);assert.equal(p.durationPercent,percent);
    assert.equal(p.colors[0],-65536);timed.story(p,0);timed.tick(ms-1);assert.equal(sub.hidden,false);
    timed.tick(ms);assert.equal(timed.story(p,ms),'done');
}
const text='První stránka zůstane stejně dlouhá. Druhá stránka se přečte rychleji a celá.';
timed.story(percentPacket(4,text,100),0);
const first=sub.textContent,firstMs=Math.ceil(Math.max(1500,Math.ceil(Array.from(first).length*1000/18))*.8);
timed.story(percentPacket(4,text,50),100);timed.tick(firstMs-1);assert.equal(sub.textContent,first);
timed.tick(firstMs);assert.notEqual(sub.textContent,first);
const secondMs=Math.ceil(Math.max(1500,Math.ceil(Array.from(sub.textContent).length*1000/18))*.4);
const second=sub.textContent;timed.tick(firstMs+secondMs-1);assert.equal(sub.textContent,second);assert.equal(sub.hidden,false);
timed.tick(firstMs+secondMs);assert.ok(sub.hidden||sub.textContent!==second);
// webOS 4.x (např. LG 43UK6300, webOS 4.4) má Chromium 53 a Node.js 8.12.
// Novější API by tam vyhodilo TypeError; UI proto musí běžet i bez nich.
const legacyContext=vm.createContext({window:{}});
vm.runInContext(`for (const [o,k] of [[String.prototype,'trimEnd'],[String.prototype,'trimStart'],[String.prototype,'padStart'],[String.prototype,'padEnd'],
 [String.prototype,'replaceAll'],[String.prototype,'matchAll'],[Array.prototype,'flat'],[Array.prototype,'flatMap'],[Array.prototype,'at'],
 [Object,'values'],[Object,'entries'],[Object,'fromEntries']]) delete o[k];`,legacyContext);
vm.runInContext(fs.readFileSync(__dirname+'/app/receiver-ui.js','utf8'),legacyContext);
const legacy=new legacyContext.window.PlayCaptionReceiverUI(document);
legacy.caption(parse(Buffer.from('PCT1\t1\t60000\t'+'Slovo '.repeat(20)+'konec.   \t\t0.5\t0.91\t0.8\t1')),0);
for(let t=0;t<60000&&!/konec\./.test(sub.textContent);t+=250)legacy.tick(t);
assert.match(sub.textContent,/konec\./);
for(const file of ['app/app.js','app/receiver-ui.js','service/service.js','service/protocol.js','service/switch-input.js','service/keepalive.js']){
    const code=fs.readFileSync(__dirname+'/'+file,'utf8').replace(/\/\/.*$/gm,'');
    assert.doesNotMatch(code,/\.(trimEnd|trimStart|padStart|padEnd|replaceAll|matchAll|flat|flatMap|at)\(|Object\.(values|entries|fromEntries)\(|\?\.|\?\?|=>|`|\basync\b|\bawait\b|\b(let|const|class)\s/,file+': syntaxe/API mimo Chromium 53 / Node 8');
}
console.log('OK: kompatibilita webOS 4.x, Story stránky/časování, procenta, živá změna bez restartu stránky, ACK, pauza/obnova a Standardní parser.');
