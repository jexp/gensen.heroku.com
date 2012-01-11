/**
 *
 */

    var apps = [
        {name:"Simple Web App with Maven and Jetty", demoUrl:"http://glowing-autumn-4004.herokuapp.com", sourceUrl:"https://github.com/jamesward/hellojavaheroku", gitUrl:"git://github.com/jamesward/hellojavaheroku.git", buildTool:"maven"},
        {name:"Simple Web App with Maven and Tomcat", demoUrl:"http://falling-journey-9776.herokuapp.com/", sourceUrl:"https://github.com/heroku/devcenter-embedded-tomcat", gitUrl:"git://github.com/heroku/devcenter-embedded-tomcat.git", buildTool:"maven"},
        {name:"Simple Play! App (v. 1.2.3)", demoUrl:"http://warm-samurai-6100.herokuapp.com", sourceUrl:"https://github.com/jamesward/helloplay", gitUrl:"git://github.com/jamesward/helloplay.git", buildTool:"play"},
        {name:"Simple Play! + Scala App (v. 1.2.3)", demoUrl:"http://hollow-summer-5131.herokuapp.com", sourceUrl:"https://github.com/jamesward/helloplayscala", gitUrl:"git://github.com/jamesward/helloplayscala.git", buildTool:"play"},
        {name:"Spring MVC & Hibernate (Spring Roo petclinic)", demoUrl:"http://floating-samurai-9174.herokuapp.com", sourceUrl:"https://github.com/jamesward/hellospringroo", gitUrl:"git://github.com/jamesward/hellospringroo.git", buildTool:"maven"},
        {name:"Java + WebSolr Add-on", demoUrl:"http://websolr-java.herokuapp.com/", sourceUrl:"https://github.com/anandbn/websolr-java", gitUrl:"git://github.com/anandbn/websolr-java.git", buildTool:"maven"},
        {name:"Scala/SBT Finagle Web App", demoUrl:"http://hollow-dawn-5005.herokuapp.com", sourceUrl:"https://github.com/jamesward/hellowebscala", gitUrl:"git://github.com/jamesward/hellowebscala.git", buildTool:"sbt"},
        {name:"Scala + Akka", demoUrl:"http://webwords.herokuapp.com", sourceUrl:"https://github.com/typesafehub/webwords/", gitUrl:"git://github.com/typesafehub/webwords.git", buildTool:"sbt"},
        {name:"Java/Gradle Web App", demoUrl:"", sourceUrl:"", gitUrl:"", buildTool:"gradle"},
        {name:"Grails Web App", demoUrl:"", sourceUrl:"", gitUrl:"", buildTool:"grails"},
        {name:"Simple Clojure/Compojure Web App", demoUrl:"http://stark-water-4282.herokuapp.com/", sourceUrl:"https://github.com/metadaddy-sfdc/HelloClojureCompojure", gitUrl:"git://github.com/metadaddy-sfdc/HelloClojureCompojure.git", buildTool:"compojure"}
    ];

    var buildTools = {
        maven:{installInstructions:'Install <a href="http://maven.apache.org/download.html">Maven</a>', buildInstruction:"mvn package"},
        sbt:{installInstructions:'Install <a href="https://github.com/harrah/xsbt/wiki/Getting-Started-Setup">SBT</a>', buildInstruction:"sbt stage"},
        play:{installInstructions:'Install <a href="http://www.playframework.org/download">Play!</a>', buildInstruction:"play run --%prod"},
        compojure:{installInstructions:'Install <a href="https://github.com/weavejester/compojure">Compojure</a> and <a href="https://github.com/technomancy/leiningen">Leiningen</a>', buildInstruction:"lein run"}
    };

    var selectedApp;

    $(function () {
        $('#shareAppButton').click(function () {
            submitForm();
        });

        $('#emailAddress').keyup(function (key) {
            if (key.which == 13) {
                submitForm();
            }
        });
        /*
                $.each(apps.reverse(), function(index, value) {
                    if (value.gitUrl != "") {
                        $("#apps").prepend('<div class="row"><label class="app"><input type="radio" name="app" value="' + value.name + '"/>' + value.name + '</label> (<a href="' + value.demoUrl + '">Live Demo</a> | <a href="' + value.sourceUrl + '">Source Code</a>)</div>');
                    }
                });
        */
        $(".youtube").colorbox({iframe:true, innerWidth:640, innerHeight:480});

        $('#emailAddress').hint();
        $('#customGitUrl').hint();

        $('#shareAppButton').attr("disabled", true);

        $("input[name=app]").change(function () {
            checkCustomGitUrlVisible();
            checkFormReady();
        });

        $("#emailAddress").keyup(function () {
            checkFormReady();
        });

        $("#customGitUrl").keyup(function () {
            checkFormReady();
        });

        checkCustomGitUrlVisible();
    });

    function checkCustomGitUrlVisible() {
        if ($("#customGitUrlRadio").attr("checked")) {
            $("#customGitUrl").show();
        }
        else {
            $("#customGitUrl").hide();
        }
    }

    function checkFormReady() {
        if ($("#customGitUrlRadio").attr("checked") && ($("#customGitUrl").val() == $("#customGitUrl")[0].title)) {
            $('#shareAppButton').attr("disabled", true);
            return;
        }

        if (($("input[name=app]:checked")) && ($("#emailAddress").val() != $("#emailAddress")[0].title) && (validateEmail($("#emailAddress").val()))) {
            $('#shareAppButton').attr("disabled", false);
        }
        else {
            $('#shareAppButton').attr("disabled", true);
        }
    }

    function submitForm() {
        var gitUrl;
        var selectedAppName = $("input[name=app]:checked").val();

        if (selectedAppName == $("#customGitUrlRadio").val()) {
            selectedApp = {name:selectedAppName, demoUrl:"", sourceUrl:"", gitUrl:$("#customGitUrl").val(), buildTool:""};
        }
        else {
            $.each(apps, function (index, value) {
                if (selectedAppName == value.name) {
                    selectedApp = value;
                }
            });
        }

        if (($('#emailAddress').val() != "") && (selectedApp.gitUrl != "")) {
            $('#createAppForm').children().attr("disabled", true);
            $('#createAppForm').fadeTo("slow", 0.35);
            $('#progressBar').show();
            $('#creatingApp').show();

            $.ajax("/", {
                type:'POST',
                data:{
                    emailAddress:$('#emailAddress').val(),
                    gitUrl:selectedApp.gitUrl
                },
                success:function (data, textStatus, jqXHR) {

                    if (data.result) {
                        $('#createAppForm').children().attr("disabled", false);
                        $('#createAppForm').hide();
                        $('#progressBar').hide();
                        $('#creatingApp').hide();
                        $('#appReady').show();

                        $('#step1').append('<a href="' + data.result.web_url + '">' + data.result.web_url + '</a>');
                        $('#step8').append('<a href="' + data.result.web_url + '">' + data.result.web_url + '</a>');

                        $('#step4').append('git clone -o heroku ' + data.result.git_url);

                        if (selectedApp.buildTool != "") {
                            $('#step2').append('<h5>c) ' + buildTools[selectedApp.buildTool].installInstructions + '</h5>');

                            $('#step6').append('cd ' + data.result.name + '<br/>');

                            $('#step6').append(buildTools[selectedApp.buildTool].buildInstruction);
                        }
                        else {
                            $('#step6').hide();
                        }
                    }
                    else if (data.error) {
                        reenableForm();

                        $.each(data.error, function (index, value) {
                            $("#errors").append(value + "<br/>");
                        });
                    }
                    else {
                        reenableForm();
                        window.alert("Unknown error occured " + data);
                    }
                },
                error:function (jqXHR, textStatus, errorThrown) {
                    reenableForm();
                    $("#errors").append(jqXHR);
                }

            });
        }
        else {
            reenableForm();
            $("#errors").append("Email address or app not specified properly");
        }
    }

    function reenableForm() {
        $('#createAppForm').children().attr("disabled", false);
        $('#createAppForm').fadeTo("slow", 1);
        $('#progressBar').hide();
        $('#creatingApp').hide();
    }

    function validateEmail(email) {
        var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
        return email.match(re)
    }

    var _gaq = _gaq || [];
    _gaq.push(['_setAccount', 'UA-26859570-1']);
    _gaq.push(['_trackPageview']);

    (function () {
        var ga = document.createElement('script');
        ga.type = 'text/javascript';
        ga.async = true;
        ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
        var s = document.getElementsByTagName('script')[0];
        s.parentNode.insertBefore(ga, s);
    })();

    function add_tag(cat, tag) {
        var e = $("#input_tags");
        var v = e.val();
        var add = cat + "/" + tag;
        if (v.split(/, */).indexOf(add) > -1) return;
        e.val(v.length == 0 ? add : v + ", " + add);
    }
