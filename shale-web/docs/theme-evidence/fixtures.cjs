// Synthetic-only interception. Every API request is fulfilled locally or aborted.
// This module is documentation tooling and is never imported by the application.
const long = 'Synthetic Meadow — ' + 'LongUnbrokenDemonstrationValue'.repeat(4);
const user = { authenticated:true,userId:1,shaleClientId:1,displayName:long,email:'synthetic@example.invalid',nameFirst:'Synthetic',nameLast:'Reviewer',isAdmin:true,isAttorney:true,initials:'SR',color:'#ffffa0' };
const member = { id:1,shaleClientId:1,displayName:long,firstName:'Synthetic',lastName:'Reviewer',email:'synthetic@example.invalid',phone:null,color:'invalid-color',initials:'SR',admin:true,attorney:true };
const status = { id:1,name:'Open — '+long,closed:false,sortOrder:0,color:'#fff',lifecycleKey:'OPEN',systemKey:'OPEN',shaleClientId:1 };
const area = { id:1,name:long,color:'#ffffa0',active:true,deleted:false,systemKey:null,shaleClientId:1 };
const caseRow = { caseId:1,caseName:long,caseNumber:'SYNTHETIC-1',caseStatus:'Open',responsibleAttorney:long,practiceArea:long,client:long,intakeDate:null };
const task = { id:12,shaleClientId:1,caseId:1,caseName:long,title:'Synthetic task — '+long,priorityId:1,dueAt:null,completedAt:null,statusId:1,assignedUserId:1,assignedUserDisplayName:long,assignedUserColor:'#ffffa0',createdByDisplayName:long,description:'Synthetic notes\n'+'UnbrokenNotes'.repeat(20) };
const contact = { id:7,shaleClientId:1,updatedAt:'2026-10-09T10:00:00Z',name:long,displayName:long,firstName:'Synthetic',lastName:'Contact',email:'synthetic@example.invalid',phone:'555-0100',phoneExtension:null,address:'Synthetic street',dateOfBirth:null,condition:'Synthetic notes',deceased:false,client:true };
const organization = { id:7,shaleClientId:1,rowVer:'AAAAAAAAAAE=',organizationTypeId:null,organizationTypeName:null,name:long,email:'synthetic@example.invalid',phone:'555-0100',phoneExtension:null,fax:null,faxExtension:null,website:'https://example.invalid/'+'long'.repeat(40),address1:'Synthetic street',address2:null,city:null,state:null,postalCode:null,country:null,notes:'Synthetic notes\n'+'LongUnbrokenNotes'.repeat(20),relatedCases:[{id:1,name:long,intakeDate:null,statuteOfLimitationsDate:null,responsibleAttorneyName:long,partyRoleName:'Synthetic role',side:null,primary:true,notes:null}] };
const detail = { ...caseRow,description:'Synthetic description\n'+'LongUnbrokenNotes'.repeat(20),summary:'Synthetic summary',rowVer:'AAAAAAAAAAE=',responsibleAttorneyId:1,practiceAreaId:1,relatedContacts:[{id:7,contactId:7,displayName:long,name:long,roleName:'Synthetic role',email:contact.email,phone:contact.phone}],statusHistory:[{caseStatusId:1,statusId:1,statusName:long,color:'#000000',lifecycleKey:'OPEN',closed:false,notes:'Synthetic status notes',effectiveDate:null,endDate:null,primary:true,current:true}],mappedCaseDates:['date_of_injury','statute_of_limitations','tort_notice_deadline'].map(systemKey=>({key:systemKey,systemKey,occurrenceId:null,caseDateTypeId:1,startsAt:null,endsAt:null,allDay:true,occurrenceRowVer:null,absent:true,absenceCaseRowVer:'AAAAAAAAAAE='})) };
const overview = {caseId:1,caseNumber:null,caseName:long,status:{id:1,name:'Open',color:'#ffffa0'},practiceArea:{id:1,name:long},responsibleAttorney:{userId:1,displayName:long},primaryLegalAssistant:null,updatedAt:'2026-10-10T12:34'};
async function intercept(context,state) {
 await context.addInitScript(()=>sessionStorage.setItem('shale-web.accessToken','synthetic-theme-review'));
 await context.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname,method=req.method(); state.calls.push({path,method});
  const fulfill=(body,statusCode=200)=>route.fulfill({status:statusCode,contentType:'application/json',body:JSON.stringify(body)});
  if(path==='/api/auth/me') return fulfill(state.nonAdmin ? {...user,isAdmin:false} : user);
  if(path==='/api/validation/contact-value') return state.advisoryInvalid ? fulfill({fieldErrors:[{field:'phone',code:'invalid_phone',message:'Synthetic invalid phone'}],message:'Check synthetic phone'},400) : fulfill({preview:'Synthetic value review'});
  const write=method!=='GET' && path!=='/api/v2/cases/search-page';
  if(write) {
   state.writes++;
   if(state.mode==='pending') { await new Promise(resolve=>state.release=resolve); return fulfill({message:'Synthetic validation failure',fieldErrors:[{field:'phone',code:'invalid_phone',message:'Synthetic phone needs review'}]},400); }
   return fulfill({message:'Synthetic validation failure',fieldErrors:[{field:'phone',code:'invalid_phone',message:'Synthetic phone needs review'}]},400);
  }
  if(state.mode==='loading') { await new Promise(resolve=>state.release=resolve); }
  if(state.mode==='error') return fulfill({message:'Synthetic unavailable'},503);
  const empty=state.mode==='empty';
  if(path==='/api/cases/assigned'||path==='/api/cases/search') return fulfill(empty?[]:[caseRow]);
  if(path==='/api/tasks/assigned'||path==='/api/cases/1/tasks') return fulfill(empty?[]:[task,{...task,id:13,title:'Completed synthetic task',completedAt:'2026-10-10T12:34'}]);
  if(path==='/api/cases/1') return fulfill(empty?null:detail);
  if(path==='/api/tasks/12') return fulfill(empty?null:task);
  if(path==='/api/cases/1/updates') return fulfill(empty?[]:[{id:1,caseId:1,noteText:'Synthetic update\n'+'LongUnbrokenNotes'.repeat(20),createdAt:null,createdByDisplayName:long}]);
  if(path==='/api/contacts/search') return fulfill(empty?[]:[contact]);
  if(path==='/api/contacts/7') return fulfill(empty?null:contact);
  if(path==='/api/organizations/search') return fulfill(empty?[]:[organization]);
  if(path==='/api/organizations/7') return fulfill(empty?null:organization);
  if(path==='/api/users') return fulfill(empty?[]:[member]);
  if(path==='/api/users/1') return fulfill(empty?null:member);
  if(path.endsWith('/case-statuses')) return fulfill(empty?[]:[status,{...status,id:2,name:'Neutral fallback',color:'invalid'}]);
  if(path.endsWith('/practice-areas')) return fulfill(empty?[]:[area]);
  if(path.endsWith('/task-priorities')) return fulfill(empty?[]:[{id:1,name:long,colorHex:'#ffffa0'}]);
  if(path.endsWith('/case-date-types')) return fulfill(['statute_of_limitations','tort_notice_deadline']);
  if(path==='/api/v2/cases/1/overview') return empty ? fulfill({message:'Unavailable'},404) : fulfill(overview);
  if(path==='/api/v2/cases/assigned-page'||path==='/api/v2/cases/search-page') return fulfill({items:empty?[]:[overview],page:0,size:25,hasMore:false});
  state.unexpected.push({path,method}); return route.abort();
 });
}
const routes = [
 ['my-shale','/my-shale'],['tasks','/tasks'],['task-detail','/tasks/12'],['cases','/cases'],['case-detail','/cases/1'],
 ['workspace','/case-workspace'],['overview','/case-workspace/1'],['contacts','/contacts'],['contact-detail','/contacts/7'],
 ['organizations','/organizations'],['organization-detail','/organizations/7'],['team','/team'],['member-detail','/team/1'],['settings','/settings']
];
const editors = [
 ['new-case','/cases','New case','#new-case-name','Create case'],
 ['case-core','/cases/1','Edit details','#case-core-name','Save details'],
 ['case-assignment','/cases/1','Edit assignment','#case-assignment-practice-area','Save assignment'],
 ['case-status','/cases/1','Edit status','input[name="case-status"]','Save status'],
 ['case-task','/cases/1','Add task','#case-task-title','Save task'],
 ['case-update','/cases/1','Add update','#case-update-note','Save update'],
 ['task-editor','/tasks/12','Edit task','#task-title','Save task'],
 ['new-contact','/contacts','New contact','#new-contact-display-name','Create contact'],
 ['contact-editor','/contacts/7','Edit contact','#contact-display-name','Save contact'],
 ['new-organization','/organizations','New organization','#new-organization-name','Create organization'],
 ['organization-editor','/organizations/7','Edit organization','#organization-name','Save organization']
];
module.exports={intercept,routes,editors};
