/**
 * Quill Image Formats module
 * Provides enhanced image support for Quill with additional formatting options
 * @author xeger
 * @license MIT
 * Source: https://github.com/xeger/quill-image-formats
 */

(function(root, factory) {
  if (typeof exports === 'object' && module.exports) {
    module.exports = factory(require('quill'));
  } else if (typeof define === 'function' && define.amd) {
    define(['quill'], factory);
  } else {
    factory(root.Quill);
  }
}(typeof self !== 'undefined' ? self : this, function(Quill) {
  const Module = Quill.import('core/module');
  const ImageFormat = Quill.import('formats/image');

  class ImageFormats extends ImageFormat {
    static create(value) {
      let node = super.create(value);

      if (typeof value === 'object') {
        Object.keys(value).forEach(key => {
          if (key === 'src') {
            node.setAttribute('src', value[key]);
          } else if (key === 'alt') {
            node.setAttribute('alt', value[key]);
          } else if (key === 'title') {
            node.setAttribute('title', value[key]);
          } else if (key === 'width') {
            if (value[key]) {
              node.style.width = typeof value[key] === 'number' ? value[key] + 'px' : value[key];
            }
          } else if (key === 'height') {
            if (value[key]) {
              node.style.height = typeof value[key] === 'number' ? value[key] + 'px' : value[key];
            }
          } else if (key === 'style') {
            Object.assign(node.style, value[key]);
          } else if (key.startsWith('data-')) {
            node.setAttribute(key, value[key]);
          }
        });
      }

      return node;
    }

    static value(node) {
      return {
        src: node.getAttribute('src'),
        alt: node.getAttribute('alt'),
        title: node.getAttribute('title'),
        width: node.style.width || null,
        height: node.style.height || null,
        style: {
          width: node.style.width,
          height: node.style.height,
          float: node.style.float,
          margin: node.style.margin
        }
      };
    }

    format(name, value) {
      const node = this.domNode;

      if (['width', 'height'].indexOf(name) > -1) {
        if (value) {
          node.style[name] = typeof value === 'number' ? value + 'px' : value;
        } else {
          node.style[name] = '';
        }
      } else if (['alt', 'title'].indexOf(name) > -1) {
        if (value) {
          node.setAttribute(name, value);
        } else {
          node.removeAttribute(name);
        }
      } else if (name === 'style') {
        if (value && typeof value === 'object') {
          Object.keys(value).forEach(key => {
            node.style[key] = value[key] || '';
          });
        }
      } else {
        return super.format(name, value);
      }
    }
  }

  ImageFormats.blotName = 'image';
  ImageFormats.tagName = 'IMG';
  ImageFormats.className = 'ql-image';

  // Register all supported formats
  Quill.register(ImageFormats, true);

  // Register additional image attributes
  const AlignStyle = Quill.import('attributors/style/align');
  const ImageAlign = new AlignStyle.constructor('align', 'imageAlign');
  Quill.register(ImageAlign, true);

  const SizeStyle = Quill.import('attributors/style/size');
  const ImageSize = new SizeStyle.constructor('size', 'imageSize');
  Quill.register(ImageSize, true);

  // Export for use
  if (typeof window !== 'undefined') {
    window.ImageFormats = ImageFormats;
  }

  return ImageFormats;
}));

