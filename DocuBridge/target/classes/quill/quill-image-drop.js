/**
 * Custom module for Quill.js to allow drag-drop and paste of images
 * Enhanced for JavaFX WebView compatibility
 * @see https://quilljs.com/blog/building-a-custom-module/
 */
class ImageDrop {
	/**
	 * Instantiate the module given a quill instance and any options
	 * @param {Quill} quill
	 * @param {Object} options
	 */
	constructor(quill, options = {}) {
		// save the quill reference
		this.quill = quill;
		this.options = options || {};

		// bind handlers to this instance
		this.handleDrop = this.handleDrop.bind(this);
		this.handlePaste = this.handlePaste.bind(this);
		this.handleDragOver = this.handleDragOver.bind(this);

		// listen for drop, paste, and dragover events
		this.quill.root.addEventListener('drop', this.handleDrop, false);
		this.quill.root.addEventListener('paste', this.handlePaste, false);
		this.quill.root.addEventListener('dragover', this.handleDragOver, false);
	}

	/**
	 * Handler for dragover event to show visual feedback
	 * @param {DragEvent} evt
	 */
	handleDragOver(evt) {
		evt.preventDefault();
		evt.dataTransfer.dropEffect = 'copy';
		this.quill.root.classList.add('drop-active');
	}

	/**
	 * Handler for drop event to read dropped files from evt.dataTransfer
	 * @param {DragEvent} evt
	 */
	handleDrop(evt) {
		evt.preventDefault();
		this.quill.root.classList.remove('drop-active');

		if (evt.dataTransfer && evt.dataTransfer.files && evt.dataTransfer.files.length) {
			// Position cursor at drop location
			if (document.caretRangeFromPoint) {
				const selection = document.getSelection();
				const range = document.caretRangeFromPoint(evt.clientX, evt.clientY);
				if (selection && range) {
					selection.setBaseAndExtent(
						range.startContainer,
						range.startOffset,
						range.startContainer,
						range.startOffset
					);
				}
			}
			// Read files and insert them
			this.readFiles(evt.dataTransfer.files, this.insert.bind(this));
		}
	}

	/**
	 * Handler for paste event to read pasted files from evt.clipboardData
	 * @param {ClipboardEvent} evt
	 */
	handlePaste(evt) {
		if (evt.clipboardData && evt.clipboardData.items && evt.clipboardData.items.length) {
			this.readFiles(evt.clipboardData.items, (dataUrl) => {
				const selection = this.quill.getSelection();
				if (!selection) {
					// Wait until after the paste for selection
					setTimeout(() => this.insert(dataUrl), 0);
				} else {
					// We have a selection, insert immediately
					this.insert(dataUrl);
				}
			});
		}
	}

	/**
	 * Insert the image into the document at the current cursor position
	 * @param {String} dataUrl  The base64-encoded image URI or image URL
	 */
	insert(dataUrl) {
		const index = (this.quill.getSelection() || {}).index || this.quill.getLength();
		this.quill.insertEmbed(index, 'image', dataUrl, 'user');
	}

	/**
	 * Extract image URIs from a list of files
	 * @param {FileList|DataTransferItemList} files  File objects or clipboard items
	 * @param {Function} callback  A function to send each data URI to
	 */
	readFiles(files, callback) {
		// Check each file for an image
		[].forEach.call(files, file => {
			// Get the actual file/blob
			let blob = file.getAsFile ? file.getAsFile() : file;

			// Check if it's a valid image type
			if (!blob || !blob.type || !blob.type.match(/^image\/(gif|jpe?g|a?png|svg|webp|bmp|vnd\.microsoft\.icon)/i)) {
				return;
			}

			// Read the file as a data URL
			if (blob instanceof Blob) {
				const reader = new FileReader();
				reader.onload = (evt) => {
					callback(evt.target.result);
				};
				reader.onerror = (evt) => {
					console.error('Error reading file:', evt);
				};
				reader.readAsDataURL(blob);
			}
		});
	}
}

// Export for module registration
if (typeof window !== 'undefined') {
	window.ImageDrop = ImageDrop;
}

