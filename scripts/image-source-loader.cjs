// Reuse miniPaint's existing memory store when the offline host disables
// IndexedDB. Upstream only selects this fallback when opening IndexedDB throws.
module.exports=function(source){
 const marker='if (window.indexedDB) {';
 if(!source.includes(marker))throw Error('The reviewed image-store initialization changed');
 return source.replace(marker,'if (!window.indexedDB) {\n                        database = { isMemory: true, images: {} };\n                    } else {');
};
