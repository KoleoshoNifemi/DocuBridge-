/**
 * Simplified Image Resizer Module for Quill - optimized for JavaFX WebView
 * MINIMAL APPROACH: No handle container in DOM - detect resize by edge proximity
 */
(function(root, factory) {
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = factory();
  } else if (typeof define === 'function' && define.amd) {
    define(factory);
  } else {
    root.QuillImageResizerWebView = factory();
  }
}(typeof self !== 'undefined' ? self : this, function() {

  class QuillImageResizerWebView {
    constructor(quill, options = {}) {
      this.quill = quill;
      this.selectedImage = null;
      this.resizing = false;

      // Inject minimal CSS for selection styling - NO DOM ELEMENTS
      if (!document.getElementById('quill-image-resizer-styles')) {
        const style = document.createElement('style');
        style.id = 'quill-image-resizer-styles';
        style.textContent = `
          .image-selected {
            outline: 2px solid #1890ff !important;
            box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.2) !important;
          }
        `;
        document.head.appendChild(style);
      }

      // Simple click handler for images
      this.quill.root.addEventListener('click', (e) => {
        if (e.target && e.target.tagName === 'IMG') {
          e.preventDefault();
          e.stopPropagation();
          this.selectImage(e.target);
        }
      }, true);

      // Mouse events for resizing
      document.addEventListener('mousemove', (e) => this.onMouseMove(e), false);
      document.addEventListener('mouseup', (e) => this.onMouseUp(e), false);

      // Deselect on click elsewhere
      document.addEventListener('click', (e) => {
        if (this.selectedImage && e.target !== this.selectedImage) {
          this.deselectImage();
        }
      }, false);

      // Double-click to delete
      this.quill.root.addEventListener('dblclick', (e) => {
        if (e.target && e.target.tagName === 'IMG' && this.selectedImage === e.target) {
          e.preventDefault();
          e.stopPropagation();
          this.deleteImage();
        }
      }, true);
    }

    selectImage(img) {
      if (this.selectedImage && this.selectedImage !== img) {
        this.deselectImage();
      }
      this.selectedImage = img;
      img.classList.add('image-selected');
      img.style.cursor = 'grab';
    }

    deselectImage() {
      if (this.selectedImage) {
        this.selectedImage.classList.remove('image-selected');
        this.selectedImage.style.cursor = 'pointer';
        this.selectedImage = null;
      }
    }


    onMouseMove(e) {
      if (!this.selectedImage) return;

      const rect = this.selectedImage.getBoundingClientRect();
      const edgeSize = 15;

      // Check if mouse is near edges (for resize detection)
      const nearLeft = e.clientX < rect.left + edgeSize;
      const nearRight = e.clientX > rect.right - edgeSize;
      const nearTop = e.clientY < rect.top + edgeSize;
      const nearBottom = e.clientY > rect.bottom - edgeSize;
      const nearEdge = (nearLeft || nearRight) && (nearTop || nearBottom);

      if (nearEdge && !this.resizing) {
        this.selectedImage.style.cursor = 'nwse-resize';
      } else if (!this.resizing) {
        this.selectedImage.style.cursor = 'grab';
      }

      // If dragging from an edge, resize
      if (this.resizing && this.selectedImage) {
        const dx = e.clientX - this.startX;
        const dy = e.clientY - this.startY;
        let w = this.startW;
        let h = this.startH;

        if (this.resizeFromRight) w = this.startW + dx;
        if (this.resizeFromLeft) w = this.startW - dx;
        if (this.resizeFromBottom) h = this.startH + dy;
        if (this.resizeFromTop) h = this.startH - dy;

        if (w > 50 && h > 50) {
          this.selectedImage.width = Math.round(w);
          this.selectedImage.height = Math.round(h);
          this.selectedImage.style.width = Math.round(w) + 'px';
          this.selectedImage.style.height = Math.round(h) + 'px';
        }
      }
    }

    onMouseDown(e) {
      if (e.target !== this.selectedImage) return;

      const rect = this.selectedImage.getBoundingClientRect();
      const edgeSize = 15;

      this.resizeFromLeft = e.clientX < rect.left + edgeSize;
      this.resizeFromRight = e.clientX > rect.right - edgeSize;
      this.resizeFromTop = e.clientY < rect.top + edgeSize;
      this.resizeFromBottom = e.clientY > rect.bottom - edgeSize;

      if ((this.resizeFromLeft || this.resizeFromRight) && (this.resizeFromTop || this.resizeFromBottom)) {
        e.preventDefault();
        this.resizing = true;
        this.startX = e.clientX;
        this.startY = e.clientY;
        this.startW = this.selectedImage.width || this.selectedImage.offsetWidth;
        this.startH = this.selectedImage.height || this.selectedImage.offsetHeight;
        document.body.style.userSelect = 'none';
      }
    }

    onMouseUp(e) {
      if (this.resizing) {
        this.resizing = false;
        document.body.style.userSelect = '';
        if (this.selectedImage) this.quill.root.dispatchEvent(new Event('input'));
      }
    }

    deleteImage() {
      if (this.selectedImage) {
        this.selectedImage.remove();
        this.deselectImage();
        this.quill.root.dispatchEvent(new Event('input'));
      }
    }
  }

  // Add mousedown listener to document to catch resize attempts
  document.addEventListener('mousedown', function(e) {
    if (e.target && e.target.classList && e.target.classList.contains('image-selected')) {
      const instance = window.quillEditorInstance;
      if (instance && instance.onMouseDown) instance.onMouseDown(e);
    }
  }, false);

  return QuillImageResizerWebView;
}));



