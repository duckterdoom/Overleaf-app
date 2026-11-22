import {readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const frontend=fileURLToPath(new URL('../',import.meta.url));
const browser=new URL('./browser/',import.meta.url);
const names=readdirSync(browser).filter(name=>/^test-[\w-]+\.mjs$/.test(name)).sort();
const [suite,...args]=process.argv.slice(2);
if(!suite||suite==='--list'){
 console.log('Usage: npm run test:browser -- <suite> <python> <fresh-test-home> [suite arguments]');
 console.log(names.map(name=>name.slice(5,-4)).join('\n'));
 process.exit(0);
}
const file=`test-${suite}.mjs`;
if(!names.includes(file)){console.error(`Unknown suite: ${suite}. Use --list.`);process.exit(2);}
const result=spawnSync(process.execPath,[fileURLToPath(new URL(file,browser)),...args],{cwd:frontend,stdio:'inherit',windowsHide:true});
if(result.error)console.error(result.error.message);
process.exit(result.status??1);
