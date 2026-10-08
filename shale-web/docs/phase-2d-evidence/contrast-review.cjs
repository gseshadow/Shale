const fs=require('node:fs'), assert=require('node:assert/strict');
const source=fs.readFileSync('shale-web/src/ui/tokens.css','utf8');
function palette(s){return Object.fromEntries([...s.matchAll(/--shale-([\w-]+):\s*(#[a-f\d]{6})/gi)].map(m=>[m[1],m[2]]));}
const light=palette(source.split('.shale-foundation[data-theme')[0]),dark={...light,...palette(source.split('.shale-foundation[data-theme')[1])};
function luminance(hex){const rgb=[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255).map(n=>n<=.04045?n/12.92:((n+.055)/1.055)**2.4);return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];}
function ratio(a,b){const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
const pairs=[['text','plane'],['secondary-text','plane'],['muted','plane'],['text','section'],['text','card'],['text','card-hover'],['secondary-text','card'],['muted','card'],['muted','card-hover'],['info-text','info'],['warning-text','warning'],['success-text','success'],['success-text','card'],['danger-text','danger'],['neutral-text','neutral'],['action-text','secondary'],['action-text','secondary-hover'],['on-dark','navigation'],['on-dark','navigation-hover'],['on-dark','primary-start'],['on-dark','primary-end']];
const boundaries=[['focus','card'],['focus','card-hover'],['control-border','card'],['control-border','secondary']];
const observations=[];
for(const [theme,tokens] of Object.entries({light,dark}))for(const [kind,items,minimum]of [['text',pairs,4.5],['boundary',boundaries,3]])for(const [fg,bg]of items){const contrast=ratio(tokens[fg],tokens[bg]);assert(contrast>=minimum,theme+' '+fg+'/'+bg+' '+contrast);observations.push({theme,kind,foreground:fg,background:bg,ratio:contrast,minimum});}
fs.writeFileSync('shale-web/docs/phase-2d-evidence/contrast-observations.json',JSON.stringify({source:'Shared ui/tokens.css pairs used by My Shale; manual computation complements axe incomplete paint checks',observations},null,2));console.log(observations.length+' token text/focus/control contrast pairs passed');
