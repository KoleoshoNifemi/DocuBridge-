// Utilities to resize, drag/drop images inside Quill (readable version)
(function (global) {
  var ImageTools = {
	init: function (quill) {
	  if (!quill || !quill.root) return;
	  this.quill = quill;
	  this.root = quill.root;
	  this.installStyles();
	  this.installOverlay();
	  this.installResize();
	  this.installDragDrop();
	},


	installStyles: function () {
	  var style = document.createElement('style');
	  style.textContent = [
		// Float-left inline-block images so text wraps beside them
		'.ql-editor img, .ql-editor .ql-image { cursor: pointer; display: inline-block; float: left; margin: 0 12px 0 0; vertical-align: top; max-width: 100%; height: auto; }'
	  ].join('\n');
	  document.head.appendChild(style);
	},

	installOverlay: function () {
	  var overlay = document.createElement('div');
	  overlay.className = 'img-resize-overlay';
	  overlay.style.position = 'absolute';
	  overlay.style.border = '0';
	  overlay.style.pointerEvents = 'none';
	  overlay.style.display = 'none';
	  overlay.style.zIndex = 10;

	  var handles = ['nw', 'ne', 'sw', 'se'].map(function (pos) {
		var h = document.createElement('div');
		h.className = 'img-resize-handle handle-' + pos;
		h.style.width = '10px';
		h.style.height = '10px';
		h.style.background = '#4285f4';
		h.style.position = 'absolute';
		h.style.pointerEvents = 'auto';
		h.style.borderRadius = '2px';
		h.dataset.pos = pos;
		overlay.appendChild(h);
		return h;
	  });

	  this.overlay = overlay;
	  this.overlayHandles = handles;
	  document.body.appendChild(overlay);

	  var self = this;
	  this.root.addEventListener('click', function (e) {
		if (e.target && e.target.tagName === 'IMG') {
		  self.activeImage = e.target;
		  self.startObservingActive();
		  self.positionOverlay();
		  self.showOverlay();
		} else if (!overlay.contains(e.target)) {
		  self.hideOverlay();
		}
	  });

	  document.addEventListener('mouseup', function (e) {
		if (!self.activeImage) return;
		if (e.target === self.activeImage || overlay.contains(e.target)) return;
		self.hideOverlay();
	  });

	  this.root.addEventListener('input', function () { self.hideOverlay(); });

	  window.addEventListener('scroll', this.positionOverlay.bind(this), true);
	  window.addEventListener('resize', this.positionOverlay.bind(this));
	},

	installResize: function () {
	  var self = this;
	  var resizing = null;

	  function onMouseMove(e) {
		if (!resizing) return;
		e.preventDefault();
		var dx = e.clientX - resizing.startX;
		var newWidth = Math.max(50, resizing.startWidth + dx);
		resizing.img.style.width = newWidth + 'px';
		resizing.img.style.height = 'auto';
		self.positionOverlay();
	  }

	  function onMouseUp() {
		resizing = null;
		document.removeEventListener('mousemove', onMouseMove);
		document.removeEventListener('mouseup', onMouseUp);
		self.hideOverlay();
	  }

	  this.overlayHandles.forEach(function (handle) {
		handle.addEventListener('mousedown', function (e) {
		  if (!self.activeImage) return;
		  e.preventDefault();
		  e.stopPropagation();
		  var rect = self.activeImage.getBoundingClientRect();
		  resizing = {
			img: self.activeImage,
			startX: e.clientX,
			startWidth: rect.width,
			aspect: rect.width / rect.height
		  };
		  document.addEventListener('mousemove', onMouseMove);
		  document.addEventListener('mouseup', onMouseUp);
		});
	  });
	},

	installDragDrop: function () {
	  var self = this;
	  var dragInfo = null;

	  this.root.addEventListener('dragstart', function (e) {
		if (e.target && e.target.tagName === 'IMG') {
		  var blot = global.Quill && global.Quill.find ? global.Quill.find(e.target) : null;
		  var index = blot && self.quill ? self.quill.getIndex(blot) : 0;
		  dragInfo = { src: e.target.getAttribute('src'), index: index };
		  e.dataTransfer.effectAllowed = 'move';
		}
	  });

	  this.root.addEventListener('dragover', function (e) {
		if (dragInfo) {
		  e.preventDefault();
		  e.dataTransfer.dropEffect = 'move';
		}
	  });

	  this.root.addEventListener('drop', function (e) {
		if (!dragInfo) return;
		e.preventDefault();
		var dropIndex = self.getIndexFromPoint(e.clientX, e.clientY);
		if (dragInfo.index != null) {
		  self.quill.deleteText(dragInfo.index, 1, 'user');
		}
		self.quill.insertEmbed(dropIndex, 'image', dragInfo.src, 'user');
		self.quill.setSelection(dropIndex + 1, 0, 'user');
		dragInfo = null;
	  });

	  this.root.addEventListener('dragend', function () {
		dragInfo = null;
	  });
	},

	getIndexFromPoint: function (x, y) {
	  var q = this.quill;
	  if (!q) return 0;
	  var doc = q.root.ownerDocument;
	  var range = (doc.caretRangeFromPoint && doc.caretRangeFromPoint(x, y)) ||
				  (doc.caretPositionFromPoint && (function () {
					var pos = doc.caretPositionFromPoint(x, y);
					if (pos) {
					  var r = doc.createRange();
					  r.setStart(pos.offsetNode, pos.offset);
					  r.collapse(true);
					  return r;
					}
				  })());
	  if (!range) return q.getLength();
	  var blot = global.Quill && global.Quill.find ? global.Quill.find(range.startContainer) : null;
	  if (!blot) return q.getLength();
	  var blotIndex = q.getIndex(blot);
	  return blotIndex + range.startOffset;
	}
  };

  ImageTools.positionOverlay = function () {
	if (!this.activeImage || !this.overlay) return;
	var rect = this.activeImage.getBoundingClientRect();
	var scrollX = window.pageXOffset || document.documentElement.scrollLeft;
	var scrollY = window.pageYOffset || document.documentElement.scrollTop;
	this.overlay.style.left = (rect.left + scrollX) + 'px';
	this.overlay.style.top = (rect.top + scrollY) + 'px';
	this.overlay.style.width = rect.width + 'px';
	this.overlay.style.height = rect.height + 'px';
	this.overlayHandles.forEach(function (h) {
	  var pos = h.dataset.pos;
	  h.style.pointerEvents = 'auto';
	  h.style.position = 'absolute';
	  h.style.top = (pos.indexOf('n') !== -1) ? '-5px' : 'calc(100% - 5px)';
	  h.style.left = (pos.indexOf('w') !== -1) ? '-5px' : 'calc(100% - 5px)';
	});
  };

  ImageTools.startObservingActive = function () {
	var self = this;
	if (self.resizeObserver) {
	  self.resizeObserver.disconnect();
	}
	if (self.activeImage && window.ResizeObserver) {
	  self.resizeObserver = new ResizeObserver(function () {
		self.positionOverlay();
	  });
	  self.resizeObserver.observe(self.activeImage);
	}
  };

  ImageTools.showOverlay = function () {
	if (!this.overlay || !this.activeImage) return;
	if (!this.overlay.parentNode) {
	  document.body.appendChild(this.overlay);
	}
	this.positionOverlay();
	this.overlay.style.display = 'block';
  };

  ImageTools.hideOverlay = function () {
	if (!this.overlay) return;
	if (this.resizeObserver) {
	  this.resizeObserver.disconnect();
	  this.resizeObserver = null;
	}
	this.overlay.style.display = 'none';
	this.overlay.style.border = '0';
	this.overlay.style.width = '0';
	this.overlay.style.height = '0';
	this.overlay.style.left = '-9999px';
	this.overlay.style.top = '-9999px';
	if (this.overlay.parentNode) {
	  this.overlay.parentNode.removeChild(this.overlay);
	}
	this.activeImage = null;
  };

  global.ImageTools = ImageTools;
  document.addEventListener('DOMContentLoaded', function () {
	if (global.quillEditor) {
	  ImageTools.init(global.quillEditor);
	} else {
	  var check = setInterval(function () {
		if (global.quillEditor) {
		  clearInterval(check);
		  ImageTools.init(global.quillEditor);
		}
	  }, 100);
	}
  });
})(window);

