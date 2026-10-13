// Reuse the actual Android project schema to test legacy editing after the desktop swap.
const fixture=require('./fixtures/android-image-studio.minimadocs-image.json');
module.exports=(png,width,height)=>{
 const p=structuredClone(fixture),layer=p.layers.find(l=>l.type==='image');
 Object.assign(p.info,{width,height,layer_active:layer.id});
 Object.assign(layer,{width,height,width_original:width,height_original:height,visible:true,params:{},opacity:100,composition:'source-over'});
 p.layers=[layer];p.data=[{id:layer.id,data:'data:image/png;base64,'+png}];return JSON.stringify(p);
};
