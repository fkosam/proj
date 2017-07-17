$(document).ready(function () {
	
	window.controllerIns=new Controller();
	controllerIns.searchFiles();
});

function Controller(){
	this.formUploadTem=Handlebars.compile($('#upload-template').html());
	this.tblTem=Handlebars.compile($('#table-template').html());
	this.noTblTem=Handlebars.compile($('#notbl').html());
	this.searchTem=Handlebars.compile($('#search-template').html());
	this.metaDataTemplate=Handlebars.compile($('#metaData-template').html());	
	handleBarsInit();
	
	function handleBarsInit(){
		Handlebars.registerHelper('creationTime', function (date) {
			return new Date(Number(date)).toLocaleString("en-US") 
		}); 
	}
	this.renderNoTbl=function(){
		$('#tbl').html(this.noTblTem({}));
	}
	this.renderTbl=function(){
		$('#tbl').html(this.tblTem(this.tblData));
	}
	this.closeContent=function(){
		$('#content').html("<div></div>");
	}
	this.showUpload=function(){
		this.metaDataCntrlIns= new MetaDataCntrl(this);
		$('#content').html(this.formUploadTem({}));
	}
	this.showSearch=function(){
		$('#content').html(this.searchTem({}));
	}
	function MetaDataCntrl(cntrlInstance){
		this.metaDataList=[];
		this.cntrlInstance=cntrlInstance;
		this.addMetaDataField=function(){
			var index=this.metaDataList.length+1;
			var metaDataItem={
					keyId:'mdfk-'+index,
					valueId:'mdfv-'+index,
					key:'',
					value:''
			};
			this.metaDataList.push(metaDataItem);
			this.render();
		}
		this.removeMetaDataField=function(itemIndex){
			this.metaDataList.splice(itemIndex,1)
			this.render();
		}
		this.setKey=function(itemIndex,text){
			this.metaDataList[itemIndex].key=text;
		}
		this.setValue=function(itemIndex,text){
			this.metaDataList[itemIndex].value=text;
		}
		this.render=function(){
			$('#metaDataContent').html(this.cntrlInstance.metaDataTemplate(this.metaDataList));
		}
	}
	this.searchFiles=function(){
		var ci=this;
		ci.searchReq={};
		ci.searchReq.fileName=$('#filename').val();
		ci.searchReq.key=$('#key').val();
		ci.searchReq.value=$('#value').val();
		$.ajax({
	        type: "POST",
	        url: "/files/searchFiles",
	        data: JSON.stringify(ci.searchReq),
	        headers: { "Content-Type": "application/json", "Accept": "application/json" },
	        cache: false,
	        success: function (data) {
	        	ci.tblData=data;
	        	if(data.length==0){
	        		ci.renderNoTbl();
	        	}else {
	        		ci.renderTbl();
	        	}
	            console.log("SUCCESS : ", data);
	        },
	        error: function (e) {
	        	alert(e);
	            console.log("ERROR : ", e);
	        }
	    });
	}
	this.deleteFile=function(fileId){
		if(!confirm("Want to delete?")){
			return;
		}
		var ci=this;
		var url="/files/deleteFile?id="+fileId;
	    $.ajax({
	        type: "GET",
	        url: url,
	        cache: false,
	        success: function (fileId) {
	        	ci.searchFiles();
	        },
	        error: function (e) {
	        	alert(e);
	            console.log("ERROR : ", e);
	        }
	    });
	}
	this.downloadFile=function(fileId,name, anchorTag){
		var url="/files/filebytes?id="+fileId;
		$(anchorTag).html('<span>Please wait downloading file...</span>')
	    $.ajax({
	        type: "GET",
	        url: url,
	        cache: false,
	        success: function (data) {
	        	download(data)
	    		$(anchorTag).html('<span>'+name+'</span>')
	        },
	        error: function (e) {
	        	alert(e);
	            console.log("ERROR : ", e);
	        }
	    });
	    function download(data){
	    	var blob = new Blob([data], {type: 'application/octet-stream'});
	    	if (typeof window.navigator.msSaveBlob !== 'undefined') {
	            window.navigator.msSaveBlob(blob, name);
	        }	    	
			var csvURL = window.URL.createObjectURL(blob);
	        var tempLink = document.createElement('a');
	        tempLink.href = csvURL;
	        tempLink.setAttribute('download', name);
	        tempLink.setAttribute('target', '_blank');
	        document.body.appendChild(tempLink);
	        tempLink.click();
	        document.body.removeChild(tempLink);
	    }
	}
	this.addFile=function(){
		var ci=this;
		$("#addFile").prop("disabled", true);
		if($('#file-Upload-field').val()==''){
			alert('Please select a file')
			$("#addFile").prop("disabled", false);
			return;
		}
		var fileSize = $('#file-Upload-field')[0].size;
		var form = $('#fileUploadForm')[0];
	    var data = new FormData(form);
	    $.ajax({
	        type: "POST",
	        enctype: 'multipart/form-data',
	        url: "/files/uploadFile",
	        data: data,
	        processData: false, 
	        contentType: false,
	        cache: false,
	        success: function (fileId) {
	        	$('#file-Upload-field').val('');
	            console.log("SUCCESS : ", data);
	            createFile(fileId);
	        },
	        error: function (e) {
	        	alert(e);
	            console.log("ERROR : ", e);
	        }
	    });
	    function createFile(fileId){
	    	
	    	var postData={};
	    	postData.fileId=fileId;
	    	postData.metaDatas=[];
	    	if(ci.metaDataCntrlIns){
	    		postData.metaDatas=ci.metaDataCntrlIns.metaDataList;
	    	}
		    $.ajax({
		        type: "POST",
		        url: "/files/addFile",
		        data: JSON.stringify(postData),
		        headers: { "Content-Type": "application/json", "Accept": "application/json" },
		        cache: false,
		        success: function (data) {
		        	ci.searchFiles();
		            console.log("SUCCESS : ", data);
		            $("#addFile").prop("disabled", false);
		        },
		        error: function (e) {
		        	alert(e);
		            console.log("ERROR : ", e);
		            $("#addFile").prop("disabled", false);

		        }
		    });
	    }
	}
}

