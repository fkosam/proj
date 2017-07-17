package f.sp;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.collect.Lists;

import f.sp.Application.Model.File;
import f.sp.Application.Model.File.MetaData;
import f.sp.Application.Model.FilePojo;
import f.sp.Application.Model.FileSearch;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
    	SpringApplication.run(Application.class, args);
   }
    
    
   public static interface Store {
	   void  saveFile(File  file);
	   boolean delete(String id);
	   List<File>  searchFiles(FileSearch  fileSearch);
	   File  findById(String id);
   }
   
   
   @Component
   public static class FileStore implements  Store{
	   List<File>  files= Lists.newArrayList();
	   static ObjectMapper  objectMapper= new ObjectMapper();
	   CollectionType typeReference ;
	   String fileStoreLocation;
	   @Autowired
	   Environment  environment;
	   public synchronized void saveFile(File file) {
		  files.add(file); 
	   }
	   
	   @PostConstruct
	   void init() throws Exception{
		   fileStoreLocation=environment.getProperty("application.filesStorage.path");
		   logger.info(" local storage location  "+fileStoreLocation);
		   if(fileStoreLocation==null){
			   throw new RuntimeException("File store location not configured, please provide property application.filesStorage.path  with read write access");
		   }
		   typeReference =TypeFactory.defaultInstance().constructCollectionType(List.class, File.class);
		   java.io.File  file= new java.io.File(fileStoreLocation);
		   if(file.exists()){
			  logger.info("********************** local storage found");
			  logger.info("********************** deserializing json ....");
			  String fileContent=FileUtils.readFileToString(file);
			  if(!StringUtils.isBlank(fileContent)){
				  List<File> fromStore= objectMapper.readValue(fileContent, typeReference);
				  files.addAll(fromStore);
			  }
		   }else{
			   logger.info("********************** creating local storage ");
			   boolean createNewFile= file.createNewFile();
			   if(!createNewFile){
				   throw new RuntimeException("Could not create file for persisting  data");
			   }
		   }
	   }
	   @PreDestroy
	   void cleanUp() throws Exception{
		   String filesListsInJsonFormat=objectMapper.writeValueAsString(files);
		   logger.info("*********************** files Lists In Json Format "+filesListsInJsonFormat);
		   logger.info("************************ persisting  files list to local storage");
		   IOUtils.write(filesListsInJsonFormat, new FileOutputStream(fileStoreLocation));
		   logger.info("done cleaup....");
	   }
	   
	   @Override
		public synchronized boolean delete(String id) {
			Optional<File> fileToDel= files.stream().filter(f->f.id.equals(id)).findFirst();
			if(fileToDel.isPresent()){
				files.remove(fileToDel.get());
				return true;
			}
			return false;
		}
	   
	   @Override
		public File findById(String id) {
		   synchronized (files) {
			   return files.stream().filter(f->f.id.equals(id)).findFirst().get();
			}
		}

	@Override
	public List<File> searchFiles(FileSearch fileSearch) {
		if(StringUtils.isBlank(fileSearch.fileName)&&StringUtils.isBlank(fileSearch.key)&&StringUtils.isBlank(fileSearch.value)){
			return Lists.newArrayList(files);
		}
		List<File> tempList= Lists.newArrayList();
		List<File>  filtered=Lists.newArrayList();
		synchronized (files) {
			tempList.addAll(files);
		}
		class A{
			void fileNameFilter(){
				List<File> list=filtered.isEmpty()? tempList:filtered;
				filtered.addAll(list.stream().filter(f->f.fileName.contains(fileSearch.fileName)).collect(Collectors.toList()));
			}
			void KeyFilter(){
				List<File> list=filtered.isEmpty()? tempList:filtered; 
				filtered.addAll(list.stream().filter(f->{
					return f.metaDatas.stream().anyMatch(md->md.key.equals(fileSearch.key));
				}).collect(Collectors.toList()));
			}
			void valueFilter(){
				List<File> list=filtered.isEmpty()? tempList:filtered;
				filtered.addAll(list.stream().filter(f->{
					return f.metaDatas.stream().anyMatch(md->md.value.equals(fileSearch.value));
				}).collect(Collectors.toList()));
			}
		}
		A a= new A();
		if(StringUtils.isNotBlank(fileSearch.fileName)){
			a.fileNameFilter();
		}
		if(StringUtils.isNotBlank(fileSearch.key)){
			a.KeyFilter();
		}
		if(StringUtils.isNotBlank(fileSearch.value)){
			a.valueFilter();
		}
		return filtered;
	}
	   
   }
   
    
    public static class Model  {
    	public static class File{
    		public byte[]  fileBytes;
    		public List<MetaData>  metaDatas= Lists.newArrayList();
    		public String id;
    		public String fileName;
    		public Date  creationTime;
    		public static class MetaData{
        		public String key;
        		public String value;
        		
        		public static enum Key{
        			FileName
        		}
        	}
    	}
    	public static class FileSearch{
    		public String fileName,key,value;
    	}
    	public static class FilePojo {
    		public List<MetaData>  metaDatas= Lists.newArrayList();
    		public String id;
    		public String fileName;
    		public Date  creationTime;
    	}
    }
    
    public static interface FileService{
    	void save(File file);
    	boolean deleteById(String id);
    	File  findById(String id);
    	List<FilePojo>  searchFiles(FileSearch  fileSearch);
    }
    
    @Component
    public static class FileServiceImpl  implements  FileService{
    	
    	@Autowired
    	Store storeIns;

    	public void save(File file) {
			file.creationTime=new Date();
			file.id=UUID.randomUUID().toString();
			storeIns.saveFile(file);
		}

		public boolean deleteById(String id) {
			return storeIns.delete(id);	
		}

		public File findById(String id) {
			return storeIns.findById(id);
		}

		public List<FilePojo> searchFiles(FileSearch fileSearch) {
			return storeIns.searchFiles(fileSearch).stream().map(f->{
				FilePojo  filePojo= new FilePojo();
				filePojo.creationTime=f.creationTime;
				filePojo.fileName=f.fileName;
				filePojo.id=f.id;
				filePojo.metaDatas=f.metaDatas;
				return filePojo;
			}).collect(Collectors.toList());
		}
    }
    
    @RestController
    @RequestMapping(value="/files")
    public static class RestImpl{
    	
    	@Autowired
    	FileService  fileService;
    	Map<String, FileData>  cache= new HashMap<>();
    	 
        @RequestMapping(value="/uploadFile",method=RequestMethod.POST)
    	public String uploadFile(@RequestParam("file")  MultipartFile  multipartFile) throws Exception{
        	String key=UUID.randomUUID().toString();
        	FileData  fileData= new FileData();
        	fileData.filaename=FilenameUtils.getName(multipartFile.getOriginalFilename());
        	fileData.filebytes=multipartFile.getBytes();
        	fileData.id=key;
        	fileData.creationTime= new  Date();
        	cache.put(key, fileData);
        	return key;
    	}
        
        @RequestMapping(value="/addFile",method=RequestMethod.POST)
        public void addFile(@RequestBody  AddFileReq  addFileReq) throws Exception{
        	FileData  fileData=cache.get(addFileReq.fileId);
        	if(fileData==null){
        		throw new RuntimeException("file not found");
        	}
        	cache.remove(addFileReq.fileId);
        	File  file= new File();
        	file.fileBytes=fileData.filebytes;
        	file.fileName=fileData.filaename;
        	file.metaDatas=addFileReq.metaDatas;
        	fileService.save(file);
        }
        
        @RequestMapping(value="/searchFiles", method=RequestMethod.POST)
        public List<FilePojo>  listFiles(@RequestBody  FileSearch  fileSearch){
        	return fileService.searchFiles(fileSearch);
        }
        @RequestMapping(value="/filebytes", method = RequestMethod.GET, produces="application/octet-stream")
        public ResponseEntity<byte[]> fileBytes(@RequestParam String id ){
        	File  file=fileService.findById(id);
        	byte[] filebytes=file.fileBytes;;
      	   	HttpHeaders responseHeaders = new HttpHeaders();
      	    responseHeaders.set("charset", "utf-8");
      	    responseHeaders.setContentType(MediaType.valueOf("application/octet-stream"));
      	    responseHeaders.setContentLength(filebytes.length);
      	    responseHeaders.set("Content-disposition", "attachment; filename="+file.fileName);
      	    return new ResponseEntity<byte[]>(filebytes, responseHeaders, HttpStatus.OK); 
        }
        
        @RequestMapping(value="/deleteFile", method=RequestMethod.GET)
        public boolean deleteFile(@RequestParam String id){
        	return fileService.deleteById(id);
        }
        
        public static class AddFileReq{
        	public String fileId;
        	public List<MetaData>  metaDatas= Lists.newArrayList();
        }
        public static class FileData {
        	String id, filaename;
        	byte[]  filebytes;
        	Date  creationTime;
        }
    }
    
    @Controller
    public static class WebController  {
    	
        @GetMapping("/")
        public String index() {
            return "home";
        }
    	
    }
    
    
    static Logger  logger=Logger.getLogger(Application.class);
    
    @Component
    public static class Jobs{
    	
    	@Autowired
    	Environment  env;
    	
    	@Autowired
    	FileService  fileService;
    	
    	 class FilesCleanerTask  extends  TimerTask{
    			
    		@Override
    		public void run() {
    			logger.info("starting file cleaner job "+new Date());
    			FileSearch  fileSearch= new FileSearch();
    			Calendar  calendar=Calendar.getInstance();
    			calendar.setTime(new Date());
    			calendar.set(Calendar.HOUR_OF_DAY, -1);
    			Date  oldCreationTime=calendar.getTime();
    			List<FilePojo> filesToRemove= fileService.searchFiles(fileSearch).stream().filter(f->f.creationTime.compareTo(oldCreationTime)==-1).collect(Collectors.toList());
    			filesToRemove.forEach(fp->fileService.deleteById(fp.id));
    		}
    	}
    	@PostConstruct
    	public void init(){
    		Timer timer = new Timer(); 
    		FilesCleanerTask fct = new FilesCleanerTask(); 
    		timer.schedule(fct, TimeUnit.MINUTES.toMillis(2),TimeUnit.MINUTES.toMillis(env.getProperty("application.filecleanerJob.repeatInterval", Long.class)));
    		
    	}
    	
    }
}
		
	
